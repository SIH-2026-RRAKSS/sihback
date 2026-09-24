package com.sih.dataservice.whatsapp.i18n;

import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * Multi-language resource bundle service for WhatsApp bot prompts and notifications (FR-WA-2, NFR-I18N-1).
 * Supports regional Indian languages with strict fallback to English ("en").
 */
@Service
public class MessageBundleService {

    private final Map<String, Map<String, String>> bundles = new HashMap<>();

    public MessageBundleService() {
        initEnglishBundle();
        initHindiBundle();
        initMarathiBundle();
        initTamilBundle();
        initTeluguBundle();
        initBengaliBundle();
        initKannadaBundle();
    }

    public String getMessage(String language, String key, Object... args) {
        String lang = normalizeLanguage(language);
        Map<String, String> langBundle = bundles.getOrDefault(lang, bundles.get("en"));
        String template = langBundle.get(key);

        if (template == null && !"en".equals(lang)) {
            // Fallback to English
            template = bundles.get("en").get(key);
        }

        if (template == null) {
            return key;
        }

        if (args != null && args.length > 0) {
            try {
                return MessageFormat.format(template, args);
            } catch (Exception e) {
                return template;
            }
        }
        return template;
    }

    public String normalizeLanguage(String lang) {
        if (lang == null || lang.isBlank()) {
            return "en";
        }
        String clean = lang.trim().toLowerCase();
        if (bundles.containsKey(clean)) {
            return clean;
        }
        return switch (clean) {
            case "hindi", "hin", "2" -> "hi";
            case "marathi", "mar", "3" -> "mr";
            case "tamil", "tam", "4" -> "ta";
            case "telugu", "tel", "5" -> "te";
            case "bengali", "ben", "6" -> "bn";
            case "kannada", "kan", "7" -> "kn";
            default -> "en";
        };
    }

    private void initEnglishBundle() {
        Map<String, String> m = new HashMap<>();
        m.put("bot.welcome", "Welcome to National Cybercrime Helpline (1930) automated reporting assistant.\n\nPlease choose your preferred language:\n1. English\n2. \u0939\u093f\u0928\u094d\u0926\u0940 (Hindi)\n3. \u092e\u0930\u0940\u0920\u0940 (Marathi)\n4. \u0ba4\u0bae\u0bbf\u0bb4\u0bcd (Tamil)\n5. \u0c24\u0c46\u0c32\u0c41\u0c17\u0c41 (Telugu)\n6. \u09ac\u09be\u0982\u09b2\u09be (Bengali)\n7. \u0c95\u0ca8\u0ccd\u0ca8\u0ca1 (Kannada)");
        m.put("bot.prompt.language", "Please reply with a number (1-7) or language name to choose your language.");
        m.put("bot.prompt.identity_link", "To verify your complaint and tie it to your Aadhaar/DigiLocker identity, please verify here: {0}\n\nReply 'DONE' once completed, or reply 'SKIP' to file as anonymous complainant.");
        m.put("bot.prompt.fraud_type", "What type of cyber fraud occurred?\n1. UPI_FRAUD\n2. PHISHING\n3. IDENTITY_THEFT\n4. INVESTMENT_SCAM\n5. JOB_FRAUD\n6. OTHER\n\nReply with the number or name.");
        m.put("bot.prompt.amount_date", "Please enter the lost amount in INR and incident date (format: AMOUNT YYYY-MM-DD, e.g., '25000 2026-09-20'):");
        m.put("bot.prompt.account_utr", "Please enter the suspect bank account number, UPI VPA, or transaction UTR reference (or reply 'NONE'):");
        m.put("bot.prompt.description", "Please provide a brief description of what happened:");
        m.put("bot.prompt.evidence", "Do you have any transaction screenshot or evidence document? Reply with a description or link, or reply 'NONE':");
        m.put("bot.prompt.confirm", "*Review your complaint details:*\n- Fraud Type: {0}\n- Amount: INR {1}\n- Incident Date: {2}\n- Suspect Account / UTR: {3}\n- Description: {4}\n\nReply *YES* to submit your complaint, or *CANCEL* to discard.");
        m.put("bot.done.message", "\u2705 *Complaint Filed Successfully!*\n\nYour Human Reference Number is: *{0}*\nStatus: *Submitted*\n\nYou will receive real-time notifications here as law enforcement investigates your case.\nYou can send 'status {0}' at any time to check progress.");
        m.put("bot.invalid_input", "We did not understand that input. {0}");
        m.put("bot.cancelled", "Your complaint draft has been cancelled. Send 'HI' anytime to start again.");
        m.put("bot.status.found", "\u2139\ufe0f *Complaint Status for {0}:*\nCurrent Status: *{1}*\nAssigned Officer: {2}\nLast Update: {3}");
        m.put("bot.status.not_found", "\u274c No complaint found matching reference *{0}*. Please check the reference number and try again.");
        m.put("notify.status.update", "\ud83d\udce2 *Cybercrime Case Update*\nReference: *{0}*\nNew Status: *{1}*\nDetails: {2}");
        m.put("notify.freeze.update", "\u2744\ufe0f *Action Taken on Case {0}*\nPolice have submitted an account freeze request to the recipient bank.");
        m.put("notify.bank.update", "\ud83c\udfe6 *Bank Response Received for {0}*\nBank has responded to freeze request: *{1}*.");
        bundles.put("en", m);
    }

    private void initHindiBundle() {
        Map<String, String> m = new HashMap<>();
        m.put("bot.welcome", "1930 \u0938\u093e\u0907\u092c\u0930 \u0939\u0947\u0932\u094d\u092a\u0932\u093e\u0907\u0928 \u092e\u0947\u0902 \u0906\u092a\u0915\u093e \u0938\u094d\u0935\u093e\u0917\u0924 \u0939\u0948\u0964\n\n\u0915\u0943\u092a\u092f\u093e \u0905\u092a\u0928\u0940 \u092d\u093e\u0937\u093e \u091a\u0941\u0928\u0947\u0902:\n1. English\n2. \u0939\u093f\u0928\u094d\u0926\u0940 (Hindi)");
        m.put("bot.prompt.language", "\u0915\u0943\u092a\u092f\u093e \u0905\u092a\u0928\u0940 \u092d\u093e\u0937\u093e \u091a\u0941\u0928\u0928\u0947 \u0915\u0947 \u0932\u093f\u090f 1-7 \u092f\u093e \u092d\u093e\u0937\u093e \u0915\u093e \u0928\u093e\u092e \u0932\u093f\u0916\u0947\u0902\u0964");
        m.put("bot.prompt.fraud_type", "\u0915\u093f\u0938 \u092a\u094d\u0930\u0915\u093e\u0930 \u0915\u093e \u0938\u093e\u0907\u092c\u0930 \u0927\u094b\u0916\u093e \u0939\u0941\u0906 \u0939\u0948?\n1. UPI_FRAUD\n2. PHISHING\n3. IDENTITY_THEFT\n4. INVESTMENT_SCAM\n5. JOB_FRAUD\n6. OTHER");
        m.put("bot.prompt.amount_date", "\u0915\u0943\u092a\u092f\u093e \u0927\u094b\u0916\u093e\u0927\u0921\u093c\u0940 \u0915\u0940 \u0930\u093e\u0936\u093f (\u0930\u0941\u092a\u092f\u0947) \u0914\u0930 \u0924\u093e\u0930\u0940\u0916 \u0926\u0930\u094d\u091c \u0915\u0930\u0947\u0902 (\u0909\u0926\u093e\u0939\u0930\u0923: 25000 2026-09-20):");
        m.put("bot.prompt.account_utr", "\u0938\u0902\u0926\u093f\u0917\u094d\u0927 \u092c\u0948\u0902\u0915 \u0916\u093e\u0924\u093e, UPI \u092f\u093e UTR \u0928\u0902\u092c\u0930 \u0932\u093f\u0916\u0947\u0902 (\u092f\u093e 'NONE' \u0932\u093f\u0916\u0947\u0902):");
        m.put("bot.prompt.description", "\u0918\u091f\u0928\u093e \u0915\u0947 \u092c\u093e\u0930\u0947 \u092e\u0947\u0902 \u0938\u0902\u0915\u094d\u0937\u0947\u092a \u092e\u0947\u0902 \u092c\u0924\u093e\u090f\u0902:");
        m.put("bot.prompt.evidence", "\u0915\u094d\u092f\u093e \u0906\u092a\u0915\u0947 \u092a\u093e\u0938 \u0915\u094b\u0908 \u0938\u092c\u0942\u0924 \u092f\u093e \u0938\u094d\u0915\u094d\u0930\u0940\u0928\u0936\u0949\u091f \u0939\u0948? \u0935\u093f\u0935\u0930\u0923 \u0926\u0947\u0902 \u092f\u093e 'NONE' \u0932\u093f\u0916\u0947\u0902:");
        m.put("bot.prompt.confirm", "*\u0936\u093f\u0915\u093e\u092f\u0924 \u0915\u093e \u0935\u093f\u0935\u0930\u0923:*\n- \u0927\u094b\u0916\u093e\u0927\u0921\u093c\u0940 \u092a\u094d\u0930\u0915\u093e\u0930: {0}\n- \u0930\u093e\u0936\u093f: \u20b9{1}\n- \u0924\u093e\u0930\u0940\u0916: {2}\n- \u0916\u093e\u0924\u093e / UTR: {3}\n- \u0935\u093f\u0935\u0930\u0923: {4}\n\n\u0936\u093f\u0915\u093e\u092f\u0924 \u0926\u0930\u094d\u091c \u0915\u0930\u0928\u0947 \u0915\u0947 \u0932\u093f\u090f *YES* \u0932\u093f\u0916\u0947\u0902 \u092f\u093e \u0930\u0926\u094d\u0926 \u0915\u0930\u0928\u0947 \u0915\u0947 \u0932\u093f\u090f *CANCEL* \u0932\u093f\u0916\u0947\u0902:");
        m.put("bot.done.message", "\u2705 *\u0936\u093f\u0915\u093e\u092f\u0924 \u0938\u092b\u0932\u0924\u093e\u092a\u0942\u0930\u094d\u0935\u0915 \u0926\u0930\u094d\u091c \u0915\u0940 \u0917\u0908!*\n\n\u0906\u092a\u0915\u093e \u0930\u0947\u092b\u0930\u0947\u0902\u0938 \u0928\u0902\u092c\u0930 \u0939\u0948: *{0}*\n\u0938\u094d\u0925\u093f\u0924\u093f \u091c\u093e\u0902\u091a\u0928\u0947 \u0915\u0947 \u0932\u093f\u090f \u0915\u092d\u0940 \u092d\u0940 'status {0}' \u092d\u0947\u091c\u0947\u0902\u0964");
        m.put("bot.invalid_input", "\u0905\u092e\u093e\u0928\u094d\u092f \u0907\u0928\u092a\u0941\u091f\u0964 {0}");
        m.put("bot.cancelled", "\u0906\u092a\u0915\u0940 \u0936\u093f\u0915\u093e\u092f\u0924 \u0930\u0926\u094d\u0926 \u0915\u0930 \u0926\u0940 \u0917\u0908 \u0939\u0948\u0964 \u092b\u093f\u0930 \u0936\u0941\u0930\u0942 \u0915\u0930\u0928\u0947 \u0915\u0947 \u0932\u093f\u090f 'HI' \u092d\u0947\u091c\u0947\u0902\u0964");
        m.put("notify.status.update", "\ud83d\udce2 *\u0938\u093e\u0907\u092c\u0930 \u0915\u0947\u0938 \u0905\u092a\u0921\u0947\u091f*\n\u0930\u0947\u092b\u0930\u0947\u0902\u0938: *{0}*\n\u0928\u0908 \u0938\u094d\u0925\u093f\u0924\u093f: *{1}*");
        bundles.put("hi", m);
    }

    private void initMarathiBundle() {
        Map<String, String> m = new HashMap<>();
        m.put("bot.welcome", "\u0930\u093e\u0937\u094d\u091f\u094d\u0930\u0940\u092f \u0938\u093e\u092f\u092c\u0930 \u0917\u0941\u0928\u094d\u0939\u0947\u0917\u093e\u0930\u0940 \u0939\u0947\u0932\u094d\u092a\u0932\u093e\u0908\u0928\u092e\u0927\u094d\u092f\u0947 \u0906\u092a\u0932\u0947 \u0938\u094d\u0935\u093e\u0917\u0924 \u0906\u0939\u0947.\n\u0915\u0943\u092a\u092f\u093e \u0906\u092a\u0932\u0940 \u092d\u093e\u0937\u093e \u0928\u093f\u0935\u0921\u093e:\n1. English\n2. Hindi\n3. Marathi");
        bundles.put("mr", m);
    }

    private void initTamilBundle() {
        Map<String, String> m = new HashMap<>();
        m.put("bot.welcome", "\u0ba4\u0bc7\u0b9a\u0bbf\u0baf \u0b9a\u0bc8\u0baa\u0bb0\u0bcd \u0b95\u0bc1\u0bb1\u0bcd\u0bb1 \u0b89\u0ba4\u0bb5\u0bbf \u0bae\u0bc8\u0baf\u0ba4\u0bcd\u0ba4\u0bbf\u0bb1\u0bcd\u0b95\u0bc1 \u0bb5\u0bb0\u0bb5\u0bc7\u0bb1\u0bcd\u0b95\u0bbf\u0bb1\u0bcb\u0bae\u0bcd.\n\u0b89\u0b99\u0bcd\u0b95\u0bb3\u0bcd \u0bae\u0bca\u0bb4\u0bbf\u0baf\u0bc8\u0ba4\u0bcd \u0ba4\u0bc7\u0bb0\u0bcd\u0ba8\u0bcd\u0ba4\u0bc6\u0b9f\u0bc1\u0b95\u0bcd\u0b95\u0bb5\u0bc1\u0bae\u0bcd:\n1. English\n4. Tamil");
        bundles.put("ta", m);
    }

    private void initTeluguBundle() {
        Map<String, String> m = new HashMap<>();
        m.put("bot.welcome", "\u0c1c\u0c3e\u0c24\u0c40\u0c2f \u0c38\u0c48\u0c2c\u0c30\u0c4d \u0c28\u0c47\u0c30 \u0c38\u0c39\u0c3e\u0c2f \u0c15\u0c47\u0c02\u0c26\u0c4d\u0c30\u0c3e\u0c28\u0c3f\u0c15\u0c3f \u0c38\u0c4d\u0c35\u0c3e\u0c17\u0c24\u0c02.\n\u0c26\u0c2f\u0c1a\u0c47\u0c38\u0c3f \u0c2e\u0c40 \u0c2d\u0c3e\u0c37\u0c28\u0c41 \u0c0e\u0c02\u0c1a\u0c41\u0c15\u0c4b\u0c02\u0c21\u0c3f:\n1. English\n5. Telugu");
        bundles.put("te", m);
    }

    private void initBengaliBundle() {
        Map<String, String> m = new HashMap<>();
        m.put("bot.welcome", "\u099c\u09be\u09a4\u09c0\u09af\u09bc \u09b8\u09be\u0987\u09ac\u09be\u09b0 \u0995\u09cd\u09b0\u09be\u0987\u09ae \u09b9\u09c7\u09b2\u09cd\u09aa\u09b2\u09be\u0987\u09a8\u09c7 \u09b8\u09cd\u09ac\u09be\u0997\u09a4\u09ae\u0964\n\u09a6\u09af\u09bc\u09be \u0995\u09b0\u09c7 \u0986\u09aa\u09a8\u09be\u09b0 \u09ad\u09be\u09b7\u09be \u09a8\u09bf\u09b0\u09cd\u09ac\u09be\u099a\u09a8 \u0995\u09b0\u09c1\u09a8:\n1. English\n6. Bengali");
        bundles.put("bn", m);
    }

    private void initKannadaBundle() {
        Map<String, String> m = new HashMap<>();
        m.put("bot.welcome", "\u0cb0\u0cbe\u0cb7\u0ccd\u0c9f\u0ccd\u0cb0\u0cc0\u0caf \u0cb8\u0cc8\u0cac\u0cb0\u0ccd \u0c85\u0caa\u0cb0\u0cbe\u0ca7 \u0cb8\u0cb9\u0cbe\u0caf\u0cb5\u0cbe\u0ca3\u0cbf\u0c97\u0cc6 \u0cb8\u0ccd\u0cb5\u0cbe\u0c97\u0ca4.\n\u0ca6\u0caf\u0cb5\u0cbf\u0c9f\u0ccd\u0c9f\u0cc1 \u0ca8\u0cbf\u0cae\u0ccd\u0cae \u0cad\u0cbe\u0cb7\u0cc6\u0caf\u0ca8\u0ccd\u0ca8\u0cc1 \u0c86\u0caf\u0ccd\u0c95\u0cc6 \u0cae\u0cbe\u0ca1\u0cbf:\n1. English\n7. Kannada");
        bundles.put("kn", m);
    }
}
