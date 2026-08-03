    # Oracle Free VM setup for self-hosted n8n

## Heads-up before you start

Oracle quietly cut the Always Free "Ampere A1" allowance in June 2026 — it used to be
4 OCPU / 24GB RAM, it's now **2 OCPU / 12GB RAM** total per tenancy (still free, just
smaller). Some regions also report "Out of host capacity" errors when trying to
provision an A1 instance because free-tier demand is high. n8n itself is light
(runs fine on 1 vCPU / 1GB RAM), so 2 OCPU/12GB is still overkill for us — but if
your region is out of A1 capacity, fall back to the **Always Free x86 "Micro" shape**
(`VM.Standard.E2.1.Micro`, 1/8 OCPU, 1GB RAM) instead. It's smaller but n8n still runs
on it fine for a handful of workflows.

## Steps

1. **Sign up** at oracle.com/cloud/free — requires a credit card for identity
   verification, but the Always Free resources are never billed as long as you stay
   within them.
2. **Create a VM instance**: Compute -> Instances -> Create Instance.
   - Name: `puduvandi-n8n`
   - Image: **Ubuntu 24.04** (Canonical Ubuntu, Always Free eligible)
   - Shape: `VM.Standard.E2.1.Micro` (x86, Always Free, 1/8 OCPU / 1GB RAM) — chosen
     over `VM.Standard.A1.Flex` after hitting "Out of host capacity" on A1 in AD-1.
     E2.1.Micro has no capacity contention. Everything below (Docker, n8n image)
     works identically on x86 — no changes needed for the smaller shape, just add a
     swapfile (step 5a) since 1GB RAM is tight.
   - Add your SSH public key (or let Oracle generate a key pair for you — download
     the private key, you won't see it again).
3. **Open the firewall** (both matter — Oracle has two layers):
   - **Security List**: Networking -> Virtual Cloud Networks -> your VCN -> Security
     Lists -> Default Security List -> Add Ingress Rules:
     - Source `0.0.0.0/0`, TCP, destination port `5678` (n8n's default port)
     - Source `0.0.0.0/0`, TCP, destination port `443` (if you add HTTPS later)
   - **OS firewall** (Ubuntu ships with `iptables` rules Oracle preconfigures) — on
     the VM: `sudo iptables -I INPUT -p tcp --dport 5678 -j ACCEPT` then persist it:
     `sudo netfilter-persistent save`
4. **SSH in**: `ssh -i /path/to/key ubuntu@<VM_PUBLIC_IP>`
5. **Install Docker**:
   ```bash
   sudo apt update && sudo apt install -y docker.io docker-compose-v2
   sudo usermod -aG docker $USER
   # log out and back in for the group change to apply
   ```
5a. **Add a 2GB swapfile** (E2.1.Micro only has 1GB RAM — Docker + n8n + the OS can
    OOM without this headroom):
    ```bash
    sudo fallocate -l 2G /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
    ```
6. **Copy `automation/n8n/docker-compose.yml` and `.env.example` to the VM** (scp or
   just paste via `nano`), fill in `.env`, then:
   ```bash
   docker compose up -d
   ```
7. n8n is reachable over HTTPS via the bundled Caddy service (see below) at
   `https://<N8N_HOST>` — n8n's first-run screen has you create an **owner
   account** (email/name/password) directly in the UI. That's your real login;
   n8n removed basic auth entirely in v1+, so there's no `N8N_BASIC_AUTH_*` env
   var to set.

## HTTPS via Caddy (already wired into docker-compose.yml)

The compose file includes a `caddy` service that reverse-proxies n8n and gets a
free auto-renewing Let's Encrypt certificate — no real domain needed. Set
`N8N_HOST`/`WEBHOOK_URL` in `.env` to `<ip-with-dashes>.nip.io` (e.g.
`129-159-224-109.nip.io` for IP `129.159.224.109`) — nip.io resolves straight to
the embedded IP with zero signup, and Let's Encrypt treats it like any other
domain. Requires ports 80 (ACME challenge + HTTP->HTTPS redirect) and 443 open
in both the Security List and the VM's iptables INPUT chain, same as port 5678
above (`sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT`, same for 443, then
`netfilter-persistent save`).

## Known gotcha: Docker containers unreachable even with correct firewall rules

If a container's published port is unreachable from the internet — or even from
`curl localhost:<port>` on the VM itself — despite correct Security List and
`INPUT` chain rules, check the **`FORWARD`** chain:
```bash
sudo iptables -L FORWARD -n
```
Oracle's Ubuntu cloud image ships with `FORWARD` policy `DROP` by default, which
silently blocks Docker's bridge-network traffic (this is different from the
`INPUT` chain, which only affects traffic addressed directly to the host, not
NAT'd container traffic). Fix:
```bash
sudo iptables -I FORWARD 3 -j ACCEPT   # insert before the trailing REJECT rule,
                                          # after DOCKER-USER/DOCKER-FORWARD jumps
sudo netfilter-persistent save
```

Sources:
- https://www.infoq.com/news/2026/07/oracle-cloud-free-tier-limits/
- https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm
