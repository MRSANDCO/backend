// Webhook controller for Twilio removed.
@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppWebhookController {

    private static final String VERIFY_TOKEN = "mrsco_verify_2026";

    @GetMapping("/webhook")
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String token,
            @RequestParam(value = "hub.challenge", required = false) String challenge) {

        if ("subscribe".equals(mode) && VERIFY_TOKEN.equals(token)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(403).body("Verification failed");
    }
}