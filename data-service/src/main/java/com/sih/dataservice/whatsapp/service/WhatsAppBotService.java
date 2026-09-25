package com.sih.dataservice.whatsapp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sih.dataservice.auth.model.UserPrincipal;
import com.sih.dataservice.common.crypto.CryptoService;
import com.sih.dataservice.complaints.dto.AccountRequestDto;
import com.sih.dataservice.complaints.dto.ComplaintResponseDto;
import com.sih.dataservice.complaints.dto.CreateComplaintRequest;
import com.sih.dataservice.complaints.entity.AccountRole;
import com.sih.dataservice.complaints.entity.Complaint;
import com.sih.dataservice.complaints.entity.ComplaintChannel;
import com.sih.dataservice.complaints.entity.EntityType;
import com.sih.dataservice.complaints.entity.PublicStatus;
import com.sih.dataservice.complaints.repository.ComplaintRepository;
import com.sih.dataservice.complaints.service.ComplaintService;
import com.sih.dataservice.users.entity.User;
import com.sih.dataservice.users.entity.UserRole;
import com.sih.dataservice.users.entity.UserStatus;
import com.sih.dataservice.users.repository.UserRepository;
import com.sih.dataservice.whatsapp.entity.WhatsAppSession;
import com.sih.dataservice.whatsapp.entity.WhatsAppSessionState;
import com.sih.dataservice.whatsapp.gateway.MessagingGateway;
import com.sih.dataservice.whatsapp.i18n.MessageBundleService;
import com.sih.dataservice.whatsapp.repository.WhatsAppSessionRepository;
import com.sih.dataservice.whatsapp.translation.TranslationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core conversation state machine for WhatsApp cybercrime reporting bot (FR-WA-1, FR-WA-2, FR-WA-3).
 */
@Service
public class WhatsAppBotService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppBotService.class);
    private static final Pattern STATUS_CMD_PATTERN = Pattern.compile("^(?:status|/status)\\s+([A-Za-z0-9-]+)$", Pattern.CASE_INSENSITIVE);

    private final WhatsAppSessionRepository sessionRepository;
    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;
    private final ComplaintService complaintService;
    private final MessagingGateway messagingGateway;
    private final MessageBundleService bundleService;
    private final TranslationService translationService;
    private final CryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public WhatsAppBotService(
            WhatsAppSessionRepository sessionRepository,
            ComplaintRepository complaintRepository,
            UserRepository userRepository,
            ComplaintService complaintService,
            MessagingGateway messagingGateway,
            MessageBundleService bundleService,
            TranslationService translationService,
            CryptoService cryptoService,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
        this.complaintService = complaintService;
        this.messagingGateway = messagingGateway;
        this.bundleService = bundleService;
        this.translationService = translationService;
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    /**
     * Entrypoint for incoming WhatsApp messages from webhooks.
     * Processes input through the conversation state machine and delivers the response.
     */
    @Transactional
    public String handleIncomingMessage(String rawPhoneNumber, String rawMessage) {
        if (rawPhoneNumber == null || rawPhoneNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number cannot be blank");
        }
        String messageText = rawMessage != null ? rawMessage.trim() : "";
        String phoneHash = cryptoService.computeHmac(rawPhoneNumber.trim());

        WhatsAppSession session = sessionRepository.findByPhoneHash(phoneHash)
                .orElseGet(() -> createNewSession(phoneHash, rawPhoneNumber));

        // Check 24-hour draft inactivity expiration
        if (session.getUpdatedAt() != null && session.getUpdatedAt().isBefore(Instant.now().minus(24, ChronoUnit.HOURS))) {
            log.info("WhatsApp draft expired due to inactivity for phoneHash={}", phoneHash);
            session.setState(WhatsAppSessionState.LANGUAGE);
            session.setDraft("{}");
        }

        String reply;

        // Global Command 1: Cancel anytime
        if ("cancel".equalsIgnoreCase(messageText) || "/cancel".equalsIgnoreCase(messageText)) {
            session.setState(WhatsAppSessionState.LANGUAGE);
            session.setDraft("{}");
            session.setUpdatedAt(Instant.now());
            sessionRepository.save(session);
            reply = bundleService.getMessage(session.getLanguage(), "bot.cancelled");
            sendOutbound(rawPhoneNumber, reply);
            return reply;
        }

        // Global Command 2: Status check anytime (e.g., "status CC-2026-000101")
        Matcher statusMatcher = STATUS_CMD_PATTERN.matcher(messageText);
        if (statusMatcher.matches()) {
            String reference = statusMatcher.group(1).trim();
            reply = handleStatusQuery(reference, session.getLanguage());
            sendOutbound(rawPhoneNumber, reply);
            return reply;
        }

        // State Machine processing
        reply = processState(session, messageText, rawPhoneNumber, phoneHash);
        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);

        sendOutbound(rawPhoneNumber, reply);
        return reply;
    }

    private String processState(WhatsAppSession session, String input, String rawPhone, String phoneHash) {
        Map<String, Object> draft = parseDraft(session.getDraft());
        String lang = session.getLanguage();

        switch (session.getState()) {
            case LANGUAGE -> {
                if (input.equalsIgnoreCase("hi") || input.equalsIgnoreCase("hello") || input.equalsIgnoreCase("start")) {
                    return bundleService.getMessage("en", "bot.welcome");
                }
                String normalizedLang = bundleService.normalizeLanguage(input);
                session.setLanguage(normalizedLang);

                // Auto-link existing user by phone hash if present
                ensureUserLinked(session, rawPhone, phoneHash);

                if (session.getUser() != null) {
                    session.setState(WhatsAppSessionState.FRAUD_TYPE);
                    return bundleService.getMessage(normalizedLang, "bot.prompt.fraud_type");
                } else {
                    session.setState(WhatsAppSessionState.IDENTITY_LINK);
                    String link = "https://cybercrime.gov.in/auth/whatsapp-link?phone=" + phoneHash.substring(0, 10);
                    return bundleService.getMessage(normalizedLang, "bot.prompt.identity_link", link);
                }
            }

            case IDENTITY_LINK -> {
                ensureUserLinked(session, rawPhone, phoneHash);
                session.setState(WhatsAppSessionState.FRAUD_TYPE);
                return bundleService.getMessage(lang, "bot.prompt.fraud_type");
            }

            case FRAUD_TYPE -> {
                String fraudType = resolveFraudType(input);
                if (fraudType == null) {
                    return bundleService.getMessage(lang, "bot.invalid_input", bundleService.getMessage(lang, "bot.prompt.fraud_type"));
                }
                draft.put("fraudType", fraudType);
                session.setDraft(serializeDraft(draft));
                session.setState(WhatsAppSessionState.AMOUNT_DATE);
                return bundleService.getMessage(lang, "bot.prompt.amount_date");
            }

            case AMOUNT_DATE -> {
                ParsedAmountDate parsed = parseAmountAndDate(input);
                if (parsed == null) {
                    return bundleService.getMessage(lang, "bot.invalid_input", bundleService.getMessage(lang, "bot.prompt.amount_date"));
                }
                draft.put("amount", parsed.amount());
                draft.put("incidentTime", parsed.date().toString());
                session.setDraft(serializeDraft(draft));
                session.setState(WhatsAppSessionState.ACCOUNT_OR_UTR);
                return bundleService.getMessage(lang, "bot.prompt.account_utr");
            }

            case ACCOUNT_OR_UTR -> {
                String accountOrUtr = input.isBlank() || "none".equalsIgnoreCase(input) ? null : input.trim();
                draft.put("accountOrUtr", accountOrUtr);
                session.setDraft(serializeDraft(draft));
                session.setState(WhatsAppSessionState.DESCRIPTION);
                return bundleService.getMessage(lang, "bot.prompt.description");
            }

            case DESCRIPTION -> {
                if (input.isBlank()) {
                    return bundleService.getMessage(lang, "bot.invalid_input", bundleService.getMessage(lang, "bot.prompt.description"));
                }
                draft.put("descriptionOriginal", input);
                String translated = translationService.translateToEnglish(input, lang);
                draft.put("descriptionEnglish", translated);
                session.setDraft(serializeDraft(draft));
                session.setState(WhatsAppSessionState.EVIDENCE);
                return bundleService.getMessage(lang, "bot.prompt.evidence");
            }

            case EVIDENCE -> {
                String evidence = input.isBlank() || "none".equalsIgnoreCase(input) ? null : input.trim();
                draft.put("evidence", evidence);
                session.setDraft(serializeDraft(draft));
                session.setState(WhatsAppSessionState.CONFIRM);

                String fraudType = (String) draft.getOrDefault("fraudType", "UNKNOWN");
                String amount = String.valueOf(draft.getOrDefault("amount", "0"));
                String incidentDate = (String) draft.getOrDefault("incidentTime", "N/A");
                String account = (String) draft.getOrDefault("accountOrUtr", "None provided");
                String desc = (String) draft.getOrDefault("descriptionOriginal", "");

                return bundleService.getMessage(lang, "bot.prompt.confirm", fraudType, amount, incidentDate, account != null ? account : "None", desc);
            }

            case CONFIRM -> {
                if (input.equalsIgnoreCase("yes") || input.equalsIgnoreCase("y") || input.equalsIgnoreCase("confirm") || input.equals("1")) {
                    String ref = submitComplaintFromDraft(session, draft, rawPhone, phoneHash);
                    session.setState(WhatsAppSessionState.DONE);
                    session.setDraft("{}");
                    return bundleService.getMessage(lang, "bot.done.message", ref);
                } else if (input.equalsIgnoreCase("no") || input.equalsIgnoreCase("cancel")) {
                    session.setState(WhatsAppSessionState.LANGUAGE);
                    session.setDraft("{}");
                    return bundleService.getMessage(lang, "bot.cancelled");
                } else {
                    return bundleService.getMessage(lang, "bot.invalid_input", "Please reply with *YES* to file your complaint, or *CANCEL* to abort.");
                }
            }

            case DONE -> {
                // Starting fresh on subsequent message
                session.setState(WhatsAppSessionState.FRAUD_TYPE);
                session.setDraft("{}");
                return bundleService.getMessage(lang, "bot.prompt.fraud_type");
            }

            default -> {
                session.setState(WhatsAppSessionState.LANGUAGE);
                return bundleService.getMessage("en", "bot.welcome");
            }
        }
    }

    private String submitComplaintFromDraft(WhatsAppSession session, Map<String, Object> draft, String rawPhone, String phoneHash) {
        User user = ensureUserLinked(session, rawPhone, phoneHash);

        CreateComplaintRequest req = new CreateComplaintRequest();
        req.setChannel(ComplaintChannel.WHATSAPP);
        req.setFraudType((String) draft.getOrDefault("fraudType", "UPI_FRAUD"));

        Object amtObj = draft.get("amount");
        BigDecimal amount = amtObj instanceof Number ? BigDecimal.valueOf(((Number) amtObj).doubleValue()) : new BigDecimal(amtObj.toString());
        req.setAmount(amount);

        String dateStr = (String) draft.get("incidentTime");
        Instant incidentTime;
        try {
            incidentTime = dateStr != null ? LocalDate.parse(dateStr).atStartOfDay(ZoneOffset.UTC).toInstant() : Instant.now();
        } catch (Exception e) {
            incidentTime = Instant.now();
        }
        req.setIncidentTime(incidentTime);

        req.setDescriptionOriginal((String) draft.getOrDefault("descriptionOriginal", "Complaint filed via WhatsApp"));
        req.setDescriptionLanguage(session.getLanguage());

        String accountOrUtr = (String) draft.get("accountOrUtr");
        if (accountOrUtr != null && !accountOrUtr.isBlank() && !"none".equalsIgnoreCase(accountOrUtr)) {
            AccountRequestDto acc = new AccountRequestDto();
            acc.setAccountNumber(accountOrUtr);
            acc.setEntityType(accountOrUtr.contains("@") ? EntityType.UPI : EntityType.ACCOUNT);
            acc.setRole(AccountRole.SUSPECT);
            req.setAccounts(List.of(acc));
        }

        UserPrincipal principal = UserPrincipal.fromUser(user);
        ComplaintResponseDto responseDto = complaintService.createComplaint(req, principal, "127.0.0.1 (WhatsApp Bot)");
        return responseDto.getHumanReference();
    }

    private User ensureUserLinked(WhatsAppSession session, String rawPhone, String phoneHash) {
        if (session.getUser() != null) {
            return session.getUser();
        }
        User user = userRepository.findByPhoneHash(phoneHash).orElseGet(() -> {
            User newUser = new User();
            newUser.setRole(UserRole.COMPLAINANT);
            newUser.setName("WhatsApp User (" + maskPhone(rawPhone) + ")");
            newUser.setPhoneHash(phoneHash);
            newUser.setPhoneEncrypted(cryptoService.encrypt(rawPhone));
            newUser.setStatus(UserStatus.ACTIVE);
            return userRepository.save(newUser);
        });
        session.setUser(user);
        return user;
    }

    private String handleStatusQuery(String reference, String language) {
        Optional<Complaint> complaintOpt = complaintRepository.findByHumanReference(reference);
        if (complaintOpt.isEmpty()) {
            return bundleService.getMessage(language, "bot.status.not_found", reference);
        }

        Complaint complaint = complaintOpt.get();
        PublicStatus publicStatus = PublicStatus.fromInternalStatus(complaint.getStatus());
        String officerName = complaint.getAssignedOfficer() != null ? complaint.getAssignedOfficer().getName() : "In Review";
        String updatedAtStr = complaint.getUpdatedAt() != null ? complaint.getUpdatedAt().toString() : "Recent";

        return bundleService.getMessage(language, "bot.status.found", reference, publicStatus.name(), officerName, updatedAtStr);
    }

    private String resolveFraudType(String input) {
        String clean = input.trim().toUpperCase();
        return switch (clean) {
            case "1", "UPI", "UPI_FRAUD" -> "UPI_FRAUD";
            case "2", "PHISHING" -> "PHISHING";
            case "3", "IDENTITY", "IDENTITY_THEFT" -> "IDENTITY_THEFT";
            case "4", "INVESTMENT", "INVESTMENT_SCAM" -> "INVESTMENT_SCAM";
            case "5", "JOB", "JOB_FRAUD" -> "JOB_FRAUD";
            case "6", "OTHER" -> "OTHER";
            default -> clean.length() >= 3 ? clean : null;
        };
    }

    private ParsedAmountDate parseAmountAndDate(String input) {
        String[] parts = input.trim().split("\\s+");
        if (parts.length == 0) {
            return null;
        }

        try {
            double amtVal = Double.parseDouble(parts[0].replace(",", ""));
            if (amtVal <= 0) {
                return null;
            }
            LocalDate date = LocalDate.now();
            if (parts.length >= 2) {
                try {
                    date = LocalDate.parse(parts[1]);
                } catch (DateTimeParseException ignored) {
                    // Fallback to today if unparseable
                }
            }
            return new ParsedAmountDate(amtVal, date);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private WhatsAppSession createNewSession(String phoneHash, String rawPhone) {
        WhatsAppSession s = new WhatsAppSession(phoneHash);
        userRepository.findByPhoneHash(phoneHash).ifPresent(s::setUser);
        return sessionRepository.save(s);
    }

    private Map<String, Object> parseDraft(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<HashMap<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String serializeDraft(Map<String, Object> draft) {
        try {
            return objectMapper.writeValueAsString(draft);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void sendOutbound(String toPhone, String text) {
        try {
            messagingGateway.sendMessage(toPhone, text);
        } catch (Exception e) {
            log.error("Failed to send outbound WhatsApp message: {}", e.getMessage());
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "***";
        }
        return phone.substring(0, 2) + "..." + phone.substring(phone.length() - 2);
    }

    private record ParsedAmountDate(double amount, LocalDate date) {}
}
