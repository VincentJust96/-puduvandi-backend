package com.puduvandi.whatsapp.service;

import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.bike.entity.Bike;
import com.puduvandi.bike.repository.BikeRepository;
import com.puduvandi.booking.dto.BookingResponse;
import com.puduvandi.booking.dto.CreateBookingRequest;
import com.puduvandi.booking.dto.PriceEstimateResponse;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.booking.service.BookingService;
import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.common.enums.DocumentType;
import com.puduvandi.common.enums.KycStatus;
import com.puduvandi.common.enums.UserRole;
import com.puduvandi.common.enums.UserStatus;
import com.puduvandi.config.RazorpayConfig;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.storage.entity.StoredFile;
import com.puduvandi.storage.service.FileStorageService;
import com.puduvandi.user.dto.UploadDocumentRequest;
import com.puduvandi.user.repository.UserDocumentRepository;
import com.puduvandi.user.service.UserService;
import com.puduvandi.whatsapp.client.WhatsAppClient;
import com.puduvandi.whatsapp.client.WhatsAppClient.Button;
import com.puduvandi.whatsapp.client.WhatsAppClient.ListRow;
import com.puduvandi.whatsapp.client.WhatsAppClient.Media;
import com.puduvandi.whatsapp.config.WhatsAppProperties;
import com.puduvandi.whatsapp.conversation.ConversationState;
import com.puduvandi.whatsapp.conversation.WhatsAppSession;
import com.puduvandi.whatsapp.conversation.WhatsAppSessionRepository;
import com.puduvandi.whatsapp.payment.PaymentLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The booking conversation, one customer message at a time:
 * location → hours/days → from → to → bike → confirm → [licence photo, if none on file] → booking (+ payment link).
 * <p>
 * Deliberately NOT @Transactional as a whole: BookingService.createBooking has its own
 * transaction, and a BusinessException thrown inside a shared one would mark it
 * rollback-only and break the session save that follows. Each repository call is its own unit.
 * <p>
 * All the real rules (overlap check, pricing, licence check, bike locking) stay in
 * BookingService — this class only collects answers and talks to the customer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppBotService {

    private static final LocalTime DEFAULT_PICKUP_TIME = LocalTime.of(10, 0);
    private static final int MAX_LIST_ROWS = 10;
    private static final int MIN_LEAD_MINUTES = 15;
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    private static final Set<String> RESTART_WORDS = Set.of("hi", "hello", "hey", "hii", "menu", "start", "restart", "book");
    private static final Set<String> CANCEL_WORDS = Set.of("cancel", "stop", "exit", "quit");

    private static final long MAX_LICENCE_BYTES = 10L * 1024 * 1024;
    private static final Map<String, String> LICENCE_TYPES = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "application/pdf", "pdf");

    private final WhatsAppSessionRepository sessionRepository;
    private final WhatsAppClient client;
    private final WhatsAppProperties properties;
    private final BikeRepository bikeRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final UserRepository userRepository;
    private final RazorpayConfig razorpayConfig;
    private final PaymentLinkService paymentLinkService;
    private final UserDocumentRepository userDocumentRepository;
    private final FileStorageService fileStorageService;
    private final UserService userService;

    public void handle(InboundMessage message) {
        String waId = message.from();
        WhatsAppSession session = sessionRepository.findByWaId(waId).orElseGet(() -> new WhatsAppSession(waId));

        LocalDateTime now = LocalDateTime.now();
        boolean expired = session.getId() != null
                && session.getLastInteractionAt().plusMinutes(properties.getSessionTtlMinutes()).isBefore(now);
        session.setLastInteractionAt(now);

        String input = message.input();
        String keyword = input.toLowerCase();

        if (!isSupported(message, session.getState())) {
            client.sendText(waId, "Sorry, I can only read text and button taps 🙏 Send HI to start booking.");
            sessionRepository.save(session);
            return;
        }
        if (session.getState() == ConversationState.START || expired || RESTART_WORDS.contains(keyword)) {
            startBooking(session);
            return;
        }
        if (CANCEL_WORDS.contains(keyword)) {
            session.reset();
            sessionRepository.save(session);
            client.sendText(waId, "Okay, cancelled. Send HI whenever you want to book a bike 🏍️");
            return;
        }

        switch (session.getState()) {
            case CHOOSING_LOCATION -> onLocation(session, input);
            case CHOOSING_MODE -> onMode(session, keyword);
            case ASKING_FROM -> onFrom(session, input);
            case ASKING_TO -> onTo(session, input);
            case CHOOSING_BIKE -> onBike(session, input);
            case CONFIRMING -> onConfirm(session, keyword);
            case AWAITING_LICENCE -> onLicence(session, message);
            default -> startBooking(session);
        }
    }

    // ===== Steps =====

    private void startBooking(WhatsAppSession session) {
        session.reset();
        List<String> areas = availableAreas();
        if (areas.isEmpty()) {
            sessionRepository.save(session);
            client.sendText(session.getWaId(), "Sorry, no bikes are available right now. Please try again later 🙏");
            return;
        }
        session.setState(ConversationState.CHOOSING_LOCATION);
        sessionRepository.save(session);
        sendLocationList(session.getWaId(), "Welcome to Puduvandi 🏍️\nWhere do you want to pick up your bike?", areas);
    }

    private void onLocation(WhatsAppSession session, String input) {
        String chosen = input.startsWith("area:") ? input.substring("area:".length()) : input;
        List<String> areas = availableAreas();
        Optional<String> match = areas.stream().filter(a -> a.equalsIgnoreCase(chosen.trim())).findFirst();
        if (match.isEmpty()) {
            sendLocationList(session.getWaId(), "Please pick a location from the list 👇", areas);
            return;
        }
        session.setArea(match.get());
        session.setState(ConversationState.CHOOSING_MODE);
        sessionRepository.save(session);
        client.sendButtons(session.getWaId(), "📍 " + match.get() + "\nDo you want the bike for hours or for days?",
                List.of(new Button("mode:HOUR", "Hours"), new Button("mode:DAY", "Days")));
    }

    private void onMode(WhatsAppSession session, String keyword) {
        String mode = null;
        if (keyword.equals("mode:hour") || keyword.startsWith("hour")) {
            mode = "HOUR";
        } else if (keyword.equals("mode:day") || keyword.startsWith("day")) {
            mode = "DAY";
        }
        if (mode == null) {
            client.sendButtons(session.getWaId(), "Please tap one 👇",
                    List.of(new Button("mode:HOUR", "Hours"), new Button("mode:DAY", "Days")));
            return;
        }
        session.setRentalMode(mode);
        session.setState(ConversationState.ASKING_FROM);
        sessionRepository.save(session);
        client.sendText(session.getWaId(),
                "📅 When do you want to pick up?\n"
                        + "Send a date like *05-10-2026*, or type *today* / *tomorrow*.\n"
                        + "Add a time if you want, e.g. *05-10-2026 14:30* (default is 10:00).");
    }

    private void onFrom(WhatsAppSession session, String input) {
        LocalDate today = LocalDate.now();
        Optional<LocalDateTime> parsed = DateInputParser.parseDateTime(input, today, DEFAULT_PICKUP_TIME);
        if (parsed.isEmpty()) {
            client.sendText(session.getWaId(),
                    "I couldn't read that date 🤔 Please send like *05-10-2026*, *05-10-2026 14:30*, *today* or *tomorrow*.");
            return;
        }
        LocalDateTime pickup = parsed.get();
        LocalDateTime earliest = LocalDateTime.now().plusMinutes(MIN_LEAD_MINUTES);
        if (pickup.isBefore(earliest)) {
            boolean defaultTimeToday = pickup.toLocalDate().equals(today) && !input.contains(":");
            if (!defaultTimeToday) {
                client.sendText(session.getWaId(), "That time has already passed. Please send a future date and time.");
                return;
            }
            pickup = earliest.withSecond(0).withNano(0); // "today" after 10:00 → start ~now
        }
        session.setPickupDatetime(pickup);
        session.setState(ConversationState.ASKING_TO);
        sessionRepository.save(session);

        String prompt = "DAY".equals(session.getRentalMode())
                ? "📅 Pickup: " + pickup.format(DISPLAY) + "\nWhen will you return it?\nSend the return date (e.g. *07-10-2026*) or the number of days (e.g. *2*)."
                : "🕒 Pickup: " + pickup.format(DISPLAY) + "\nFor how many hours do you need it? Send a number, e.g. *4*.";
        client.sendText(session.getWaId(), prompt);
    }

    private void onTo(WhatsAppSession session, String input) {
        LocalDateTime pickup = session.getPickupDatetime();
        LocalDateTime returnAt = null;
        boolean dayMode = "DAY".equals(session.getRentalMode());

        Optional<Integer> quantity = DateInputParser.parseQuantity(input);
        if (quantity.isPresent()) {
            returnAt = dayMode ? pickup.plusDays(quantity.get()) : pickup.plusHours(quantity.get());
        } else if (dayMode) {
            returnAt = DateInputParser.parseDate(input, LocalDate.now())
                    .map(date -> date.atTime(pickup.toLocalTime()))
                    .orElse(null);
        }
        if (returnAt == null) {
            client.sendText(session.getWaId(), dayMode
                    ? "Please send a return date like *07-10-2026*, or a number of days like *2*."
                    : "Please send the number of hours, e.g. *4*.");
            return;
        }
        if (!returnAt.isAfter(pickup.plusMinutes(59))) {
            client.sendText(session.getWaId(), "The minimum booking is 1 hour, and the return must be after pickup. Please try again.");
            return;
        }
        session.setReturnDatetime(returnAt);
        showBikes(session);
    }

    private void showBikes(WhatsAppSession session) {
        List<Bike> bikes = freeBikes(session);
        if (bikes.isEmpty()) {
            session.reset();
            sessionRepository.save(session);
            client.sendText(session.getWaId(),
                    "Sorry, no bikes are free in that area for those dates 😔 Send HI to try different dates or another location.");
            return;
        }
        session.setState(ConversationState.CHOOSING_BIKE);
        sessionRepository.save(session);

        List<ListRow> rows = bikes.stream()
                .map(b -> new ListRow("bike:" + b.getId(), b.getBrand() + " " + b.getModel(),
                        "₹" + b.getPricePerHour().stripTrailingZeros().toPlainString() + "/hr · ₹"
                                + b.getPricePerDay().stripTrailingZeros().toPlainString() + "/day · deposit ₹"
                                + b.getSecurityDeposit().stripTrailingZeros().toPlainString()))
                .toList();
        client.sendList(session.getWaId(),
                "🏍️ Bikes free in " + session.getArea() + "\n" + session.getPickupDatetime().format(DISPLAY)
                        + " → " + session.getReturnDatetime().format(DISPLAY) + "\nPick one 👇",
                "See bikes", "Available bikes", rows);
    }

    private void onBike(WhatsAppSession session, String input) {
        Long bikeId = parseId(input, "bike:");
        if (bikeId == null) {
            client.sendText(session.getWaId(), "Please pick a bike from the list 👇");
            showBikes(session);
            return;
        }
        session.setBikeId(bikeId);
        try {
            sendSummary(session);
            session.setState(ConversationState.CONFIRMING);
            sessionRepository.save(session);
        } catch (BusinessException | ResourceNotFoundException ex) {
            client.sendText(session.getWaId(), ex.getMessage());
            showBikes(session);
        }
    }

    private void onConfirm(WhatsAppSession session, String keyword) {
        if (keyword.equals("confirm:no") || keyword.equals("no")) {
            session.reset();
            sessionRepository.save(session);
            client.sendText(session.getWaId(), "No problem, booking cancelled. Send HI to start again 🏍️");
            return;
        }
        if (!(keyword.equals("confirm:yes") || keyword.equals("yes"))) {
            client.sendText(session.getWaId(), "Please tap *Book now* or *Cancel* 👇");
            try {
                sendSummary(session);
            } catch (BusinessException | ResourceNotFoundException ex) {
                client.sendText(session.getWaId(), ex.getMessage() + "\nSend HI to start again.");
                session.reset();
                sessionRepository.save(session);
            }
            return;
        }
        book(session);
    }

    private void book(WhatsAppSession session) {
        try {
            User customer = findOrCreateCustomer(session.getWaId());
            if (!hasLicence(customer)) {
                session.setState(ConversationState.AWAITING_LICENCE);
                sessionRepository.save(session);
                client.sendText(session.getWaId(),
                        "🪪 One last step: we need your driving licence.\n"
                                + "Please send a clear photo of it here (front side, all text readable).\n"
                                + "Type CANCEL to stop.");
                return;
            }
            BookingResponse booking = bookingService.createBooking(customer.getId(),
                    new CreateBookingRequest(session.getBikeId(), session.getPickupDatetime(),
                            session.getReturnDatetime(), "SELF_PICKUP", null, null));
            session.reset();
            sessionRepository.save(session);

            // CONFIRMED (payments in mock mode): BookingConfirmationService has already messaged them.
            if (booking.status() == BookingStatus.PAYMENT_PENDING) {
                String link = paymentLinkService.createLink(booking.id());
                client.sendText(session.getWaId(),
                        "🎉 Your bike is held for " + razorpayConfig.getPaymentExpiryMinutes() + " minutes.\n"
                                + "Booking: " + booking.bookingReference() + "\n"
                                + "Amount to pay: ₹" + booking.totalAmount().stripTrailingZeros().toPlainString() + "\n\n"
                                + "Pay securely here (no login needed): " + link + "\n\n"
                                + "Once paid, your confirmation will arrive right here on WhatsApp ✅");
            }
        } catch (BusinessException | ResourceNotFoundException ex) {
            session.reset();
            sessionRepository.save(session);
            client.sendText(session.getWaId(), ex.getMessage() + "\nSend HI to start again.");
        }
    }

    /** The licence photo: store it like a website upload would, then carry on with the booking. */
    private void onLicence(WhatsAppSession session, InboundMessage message) {
        String mimeType = message.mediaMimeType() == null ? null
                : message.mediaMimeType().split(";")[0].trim().toLowerCase();
        String extension = mimeType == null ? null : LICENCE_TYPES.get(mimeType);
        if (!message.hasMedia() || extension == null) {
            client.sendText(session.getWaId(),
                    "Please send a *photo* of your driving licence 📷 (JPG, PNG or PDF). Type CANCEL to stop.");
            return;
        }
        Optional<Media> media = client.downloadMedia(message.mediaId(), MAX_LICENCE_BYTES);
        if (media.isEmpty()) {
            client.sendText(session.getWaId(),
                    "Sorry, I couldn't get that file 😕 Please send the photo again (under 10 MB).");
            return;
        }
        User customer = findOrCreateCustomer(session.getWaId());
        StoredFile stored = fileStorageService.store(media.get().content(), "whatsapp-licence." + extension,
                mimeType, customer.getId(), "USER_DOCUMENT");
        userService.uploadDocument(customer.getId(),
                new UploadDocumentRequest(DocumentType.DRIVING_LICENSE, stored.getFileUrl()));
        log.info("Driving licence received via WhatsApp: userId={}, fileId={}", customer.getId(), stored.getId());

        client.sendText(session.getWaId(), "✅ Licence received, thank you! Booking your bike now…");
        book(session);
    }

    // ===== Helpers =====

    private boolean hasLicence(User customer) {
        return userDocumentRepository
                .findByUserIdAndDocumentTypeAndDeletedFalse(customer.getId(), DocumentType.DRIVING_LICENSE)
                .isPresent();
    }

    private void sendSummary(WhatsAppSession session) {
        PriceEstimateResponse estimate = bookingService.estimatePrice(
                session.getBikeId(), session.getPickupDatetime(), session.getReturnDatetime());
        client.sendButtons(session.getWaId(),
                "Please confirm your booking 👇\n"
                        + "🏍️ " + estimate.bikeBrand() + " " + estimate.bikeModel() + "\n"
                        + "📍 " + session.getArea() + "\n"
                        + "🕒 " + session.getPickupDatetime().format(DISPLAY) + "\n"
                        + "🕒 " + session.getReturnDatetime().format(DISPLAY) + "\n"
                        + "Rent: ₹" + estimate.baseAmount().stripTrailingZeros().toPlainString() + "\n"
                        + "Security deposit: ₹" + estimate.securityDeposit().stripTrailingZeros().toPlainString() + "\n"
                        + "*Total: ₹" + estimate.totalAmount().stripTrailingZeros().toPlainString() + "*",
                List.of(new Button("confirm:yes", "Book now"), new Button("confirm:no", "Cancel")));
    }

    private void sendLocationList(String waId, String body, List<String> areas) {
        List<ListRow> rows = areas.stream().map(a -> new ListRow("area:" + a, a, null)).toList();
        client.sendList(waId, body, "Choose location", "Pickup locations", rows);
    }

    private List<String> availableAreas() {
        return bikeRepository.findAvailableAreas().stream().limit(MAX_LIST_ROWS).toList();
    }

    /** Approved, available bikes in the chosen area with no overlapping booking for the dates. */
    private List<Bike> freeBikes(WhatsAppSession session) {
        return bikeRepository.browseAvailableBikes(null, null, session.getArea(), null, null,
                        null, null, null, null, PageRequest.of(0, 50)).getContent().stream()
                .filter(b -> b.getArea() != null && b.getArea().equalsIgnoreCase(session.getArea()))
                .filter(b -> !bookingRepository.existsOverlappingBooking(
                        b.getId(), session.getPickupDatetime(), session.getReturnDatetime()))
                .limit(MAX_LIST_ROWS)
                .toList();
    }

    /** WhatsApp identifies people by phone number, so that number is their account. */
    User findOrCreateCustomer(String waId) {
        String phone = toLocalNumber(waId);
        if (phone == null) {
            throw new BusinessException("Sorry, WhatsApp booking is only available for Indian mobile numbers right now.");
        }
        Optional<User> existing = userRepository.findByPhoneNumberAndDeletedFalse(phone);
        if (existing.isEmpty() && userRepository.existsByPhoneNumber(phone)) {
            throw new BusinessException("We couldn't use this number. Please contact support.");
        }
        User user = existing.orElseGet(() -> {
            User created = userRepository.save(User.builder()
                    .phoneNumber(phone)
                    .role(UserRole.CUSTOMER)
                    .status(UserStatus.ACTIVE)
                    .kycStatus(KycStatus.NOT_SUBMITTED)
                    .deleted(false)
                    .build());
            log.info("New user registered via WhatsApp: phone={}", phone);
            return created;
        });
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException("Your account has been suspended. Please contact support.");
        }
        if (user.getRole() == null) {
            user.setRole(UserRole.CUSTOMER);
            userRepository.save(user);
        }
        return user;
    }

    /** 919876543210 → 9876543210 (how users are stored); null if not an Indian mobile. */
    static String toLocalNumber(String waId) {
        if (waId == null) {
            return null;
        }
        String digits = waId.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        return digits.matches("^[6-9]\\d{9}$") ? digits : null;
    }

    private static Long parseId(String input, String prefix) {
        if (!input.startsWith(prefix)) {
            return null;
        }
        try {
            return Long.parseLong(input.substring(prefix.length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean isSupported(InboundMessage message, ConversationState state) {
        return "text".equals(message.type())
                || ("interactive".equals(message.type()) && message.replyId() != null)
                || (state == ConversationState.AWAITING_LICENCE && message.hasMedia());
    }
}
