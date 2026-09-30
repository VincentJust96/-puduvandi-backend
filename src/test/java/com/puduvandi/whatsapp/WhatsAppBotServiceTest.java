package com.puduvandi.whatsapp;

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
import com.puduvandi.common.enums.UserRole;
import com.puduvandi.common.enums.UserStatus;
import com.puduvandi.config.RazorpayConfig;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.storage.entity.StoredFile;
import com.puduvandi.storage.service.FileStorageService;
import com.puduvandi.user.dto.UploadDocumentRequest;
import com.puduvandi.user.entity.UserDocument;
import com.puduvandi.user.repository.UserDocumentRepository;
import com.puduvandi.user.service.UserService;
import com.puduvandi.whatsapp.client.WhatsAppClient;
import com.puduvandi.whatsapp.client.WhatsAppClient.Media;
import com.puduvandi.whatsapp.config.WhatsAppProperties;
import com.puduvandi.whatsapp.conversation.ConversationState;
import com.puduvandi.whatsapp.conversation.WhatsAppSession;
import com.puduvandi.whatsapp.conversation.WhatsAppSessionRepository;
import com.puduvandi.whatsapp.payment.PaymentLinkService;
import com.puduvandi.whatsapp.service.InboundMessage;
import com.puduvandi.whatsapp.service.WhatsAppBotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WhatsAppBotService conversation flow")
class WhatsAppBotServiceTest {

    private static final String WA_ID = "919876543210";

    @Mock private WhatsAppSessionRepository sessionRepository;
    @Mock private WhatsAppClient client;
    @Mock private BikeRepository bikeRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private BookingService bookingService;
    @Mock private UserRepository userRepository;
    @Mock private PaymentLinkService paymentLinkService;
    @Mock private UserDocumentRepository userDocumentRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private UserService userService;

    private WhatsAppProperties properties;
    private RazorpayConfig razorpayConfig;
    private WhatsAppBotService bot;
    private WhatsAppSession session;

    @BeforeEach
    void setUp() {
        properties = new WhatsAppProperties();
        razorpayConfig = new RazorpayConfig();
        bot = new WhatsAppBotService(sessionRepository, client, properties, bikeRepository,
                bookingRepository, bookingService, userRepository, razorpayConfig, paymentLinkService, userDocumentRepository, fileStorageService, userService);
        session = new WhatsAppSession(WA_ID);
        lenient().when(sessionRepository.findByWaId(WA_ID)).thenAnswer(inv -> Optional.of(session));
        session.setId(1L);
    }

    private void say(String text) {
        bot.handle(new InboundMessage(WA_ID, "wamid." + System.nanoTime(), "text", text, null));
    }

    private void tap(String replyId) {
        bot.handle(new InboundMessage(WA_ID, "wamid." + System.nanoTime(), "interactive", null, replyId));
    }

    private Bike bike(long id) {
        Bike b = Bike.builder().brand("Honda").model("Activa").area("Pondy")
                .pricePerHour(new BigDecimal("50")).pricePerDay(new BigDecimal("400"))
                .securityDeposit(new BigDecimal("500")).build();
        b.setId(id);
        return b;
    }

    private void licenceOnFile(boolean present) {
        when(userDocumentRepository.findByUserIdAndDocumentTypeAndDeletedFalse(42L, DocumentType.DRIVING_LICENSE))
                .thenReturn(present ? Optional.of(UserDocument.builder().build()) : Optional.empty());
    }

    /** A chat that has reached "Book now", for customer 42. */
    private void readyToConfirm() {
        session.setLastInteractionAt(LocalDateTime.now());
        session.setState(ConversationState.CONFIRMING);
        session.setArea("Pondy");
        session.setBikeId(7L);
        session.setPickupDatetime(LocalDateTime.now().plusDays(1));
        session.setReturnDatetime(LocalDateTime.now().plusDays(2));
        User customer = User.builder().phoneNumber("9876543210").role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE).deleted(false).build();
        customer.setId(42L);
        lenient().when(userRepository.findByPhoneNumberAndDeletedFalse("9876543210")).thenReturn(Optional.of(customer));
    }

    private void sendPhoto(String type, String mimeType) {
        bot.handle(new InboundMessage(WA_ID, "wamid." + System.nanoTime(), type, null, null, "media-1", mimeType));
    }

    @Test
    @DisplayName("first message greets and shows the location list")
    void greeting() {
        session.setId(null);
        session.setLastInteractionAt(LocalDateTime.now());
        when(bikeRepository.findAvailableAreas()).thenReturn(List.of("Pondy", "Auroville"));

        say("hello");

        assertThat(session.getState()).isEqualTo(ConversationState.CHOOSING_LOCATION);
        verify(client).sendList(eq(WA_ID), contains("Where"), anyString(), anyString(), argThat(rows -> rows.size() == 2));
    }

    @Test
    @DisplayName("happy path: location → days → dates → bike → confirm creates the booking and sends the pay link")
    void happyPath() {
        session.setLastInteractionAt(LocalDateTime.now());
        session.setState(ConversationState.CHOOSING_LOCATION);
        when(bikeRepository.findAvailableAreas()).thenReturn(List.of("Pondy"));
        tap("area:Pondy");
        assertThat(session.getState()).isEqualTo(ConversationState.CHOOSING_MODE);

        tap("mode:DAY");
        assertThat(session.getState()).isEqualTo(ConversationState.ASKING_FROM);

        say(LocalDate.now().plusDays(3).format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")));
        assertThat(session.getState()).isEqualTo(ConversationState.ASKING_TO);

        when(bikeRepository.browseAvailableBikes(any(), any(), eq("Pondy"), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(bike(7))));
        when(bookingRepository.existsOverlappingBooking(eq(7L), any(), any())).thenReturn(false);

        say("2");
        assertThat(session.getState()).isEqualTo(ConversationState.CHOOSING_BIKE);
        assertThat(session.getReturnDatetime()).isEqualTo(session.getPickupDatetime().plusDays(2));

        when(bookingService.estimatePrice(eq(7L), any(), any())).thenReturn(new PriceEstimateResponse(
                7L, "Honda", "Activa", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE,
                new BigDecimal("800"), new BigDecimal("500"), new BigDecimal("1300"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        tap("bike:7");
        assertThat(session.getState()).isEqualTo(ConversationState.CONFIRMING);

        User customer = User.builder().phoneNumber("9876543210").role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE).deleted(false).build();
        customer.setId(42L);
        when(userRepository.findByPhoneNumberAndDeletedFalse("9876543210")).thenReturn(Optional.of(customer));
        licenceOnFile(true);
        BookingResponse booking = mock(BookingResponse.class);
        when(booking.status()).thenReturn(BookingStatus.PAYMENT_PENDING);
        when(booking.id()).thenReturn(99L);
        when(booking.bookingReference()).thenReturn("PV-0001");
        when(paymentLinkService.createLink(99L)).thenReturn("http://localhost:8080/api/v1/pay/5.nonce.sig");
        when(booking.totalAmount()).thenReturn(new BigDecimal("1300.00"));
        when(bookingService.createBooking(eq(42L), any(CreateBookingRequest.class))).thenReturn(booking);

        tap("confirm:yes");

        ArgumentCaptor<CreateBookingRequest> request = ArgumentCaptor.forClass(CreateBookingRequest.class);
        verify(bookingService).createBooking(eq(42L), request.capture());
        assertThat(request.getValue().bikeId()).isEqualTo(7L);
        assertThat(request.getValue().deliveryType()).isEqualTo("SELF_PICKUP");
        verify(client).sendText(eq(WA_ID), contains("http://localhost:8080/api/v1/pay/5.nonce.sig"));
        assertThat(session.getState()).isEqualTo(ConversationState.START);
    }

    @Test
    @DisplayName("a booking rule failure (e.g. bike just taken) is told to the customer and resets the chat")
    void businessFailure() {
        session.setLastInteractionAt(LocalDateTime.now());
        session.setState(ConversationState.CONFIRMING);
        session.setBikeId(7L);
        session.setPickupDatetime(LocalDateTime.now().plusDays(1));
        session.setReturnDatetime(LocalDateTime.now().plusDays(2));
        User customer = User.builder().phoneNumber("9876543210").role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE).deleted(false).build();
        customer.setId(42L);
        when(userRepository.findByPhoneNumberAndDeletedFalse("9876543210")).thenReturn(Optional.of(customer));
        licenceOnFile(true);
        when(bookingService.createBooking(eq(42L), any()))
                .thenThrow(new BusinessException("Bike is not available for the selected dates."));

        tap("confirm:yes");

        verify(client).sendText(eq(WA_ID), contains("not available"));
        assertThat(session.getState()).isEqualTo(ConversationState.START);
    }

    @Test
    @DisplayName("bad date input keeps the step and asks again")
    void badDate() {
        session.setLastInteractionAt(LocalDateTime.now());
        session.setState(ConversationState.ASKING_FROM);
        session.setRentalMode("DAY");

        say("next friday");

        assertThat(session.getState()).isEqualTo(ConversationState.ASKING_FROM);
        verify(client).sendText(eq(WA_ID), contains("couldn't read"));
    }

    @Test
    @DisplayName("an expired session restarts from the greeting")
    void expiredSessionRestarts() {
        session.setState(ConversationState.CONFIRMING);
        session.setLastInteractionAt(LocalDateTime.now().minusHours(3));
        when(bikeRepository.findAvailableAreas()).thenReturn(List.of("Pondy"));

        say("yes");

        assertThat(session.getState()).isEqualTo(ConversationState.CHOOSING_LOCATION);
        verify(bookingService, never()).createBooking(any(), any());
    }

    @Test
    @DisplayName("'cancel' clears the session")
    void cancelResets() {
        session.setLastInteractionAt(LocalDateTime.now());
        session.setState(ConversationState.ASKING_TO);
        session.setArea("Pondy");

        say("cancel");

        assertThat(session.getState()).isEqualTo(ConversationState.START);
        assertThat(session.getArea()).isNull();
    }

    @Test
    @DisplayName("images and other unsupported messages get a polite nudge")
    void unsupportedType() {
        session.setLastInteractionAt(LocalDateTime.now());
        session.setState(ConversationState.ASKING_TO);

        bot.handle(new InboundMessage(WA_ID, "wamid.img", "image", null, null));

        verify(client).sendText(eq(WA_ID), contains("only read text"));
        assertThat(session.getState()).isEqualTo(ConversationState.ASKING_TO);
    }

    @Test
    @DisplayName("no licence on file: 'Book now' asks for a photo instead of booking")
    void asksForLicence() {
        readyToConfirm();
        licenceOnFile(false);

        tap("confirm:yes");

        assertThat(session.getState()).isEqualTo(ConversationState.AWAITING_LICENCE);
        assertThat(session.getBikeId()).isEqualTo(7L);
        verify(client).sendText(eq(WA_ID), contains("photo"));
        verify(bookingService, never()).createBooking(any(), any());
    }

    @Test
    @DisplayName("licence photo is stored privately, saved as the licence, and the booking goes through")
    void licencePhotoCompletesBooking() {
        readyToConfirm();
        session.setState(ConversationState.AWAITING_LICENCE);
        when(client.downloadMedia(eq("media-1"), anyLong()))
                .thenReturn(Optional.of(new Media(new byte[]{1, 2, 3}, "image/jpeg")));
        StoredFile stored = StoredFile.builder().fileUrl("/api/v1/files/55").build();
        when(fileStorageService.store(any(byte[].class), eq("whatsapp-licence.jpg"), eq("image/jpeg"), eq(42L), eq("USER_DOCUMENT")))
                .thenReturn(stored);
        licenceOnFile(true); // what book() sees after the upload
        BookingResponse booking = mock(BookingResponse.class);
        when(booking.status()).thenReturn(BookingStatus.PAYMENT_PENDING);
        when(booking.id()).thenReturn(99L);
        when(booking.bookingReference()).thenReturn("PV-0001");
        when(booking.totalAmount()).thenReturn(new BigDecimal("1300"));
        when(bookingService.createBooking(eq(42L), any())).thenReturn(booking);
        when(paymentLinkService.createLink(99L)).thenReturn("http://localhost:8080/api/v1/pay/5.nonce.sig");

        sendPhoto("image", "image/jpeg");

        ArgumentCaptor<UploadDocumentRequest> doc = ArgumentCaptor.forClass(UploadDocumentRequest.class);
        verify(userService).uploadDocument(eq(42L), doc.capture());
        assertThat(doc.getValue().documentType()).isEqualTo(DocumentType.DRIVING_LICENSE);
        assertThat(doc.getValue().documentUrl()).isEqualTo("/api/v1/files/55");
        verify(client).sendText(eq(WA_ID), contains("Licence received"));
        verify(client).sendText(eq(WA_ID), contains("/api/v1/pay/5.nonce.sig"));
        assertThat(session.getState()).isEqualTo(ConversationState.START);
    }

    @Test
    @DisplayName("while waiting for the licence, text or a wrong file type asks again")
    void licenceWrongInput() {
        readyToConfirm();
        session.setState(ConversationState.AWAITING_LICENCE);

        say("here it is");
        sendPhoto("document", "application/zip");

        verify(client, times(2)).sendText(eq(WA_ID), contains("photo"));
        verify(client, never()).downloadMedia(any(), anyLong());
        verifyNoInteractions(fileStorageService, userService);
        assertThat(session.getState()).isEqualTo(ConversationState.AWAITING_LICENCE);
    }

    @Test
    @DisplayName("a failed download asks for the photo again and stores nothing")
    void licenceDownloadFails() {
        readyToConfirm();
        session.setState(ConversationState.AWAITING_LICENCE);
        when(client.downloadMedia(eq("media-1"), anyLong())).thenReturn(Optional.empty());

        sendPhoto("image", "image/png");

        verify(client).sendText(eq(WA_ID), contains("couldn't get that file"));
        verifyNoInteractions(fileStorageService, userService);
        assertThat(session.getState()).isEqualTo(ConversationState.AWAITING_LICENCE);
    }
}
