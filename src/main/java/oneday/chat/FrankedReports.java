package oneday.chat;

import oneday.common.ApiException;
import oneday.safety.ReportCategory;
import oneday.safety.SafetyService;
import oneday.safety.SafetyService.ReportReceipt;
import oneday.safety.SafetyService.SafetyTarget;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reporting an end-to-end encrypted message with verifiable evidence (see {@link Franking}). */
@Component
public class FrankedReports {

	static final int MAX_EVIDENCE = 4000;

	private final ChatService chat;

	private final SafetyService safety;

	FrankedReports(ChatService chat, SafetyService safety) {
		this.chat = chat;
		this.safety = safety;
	}

	@Transactional
	public ReportReceipt report(String userId, String conversationId, String messageId, ReportCategory category,
			String plaintext, byte[] frankingKey, String details, boolean alsoBlock) {
		if (plaintext == null || plaintext.length() > MAX_EVIDENCE) {
			throw ApiException.badRequest("INVALID_PLAINTEXT", "Include the message text as you received it");
		}
		String connectionId = chat.verifyFranking(userId, conversationId, messageId, plaintext, frankingKey)
			.orElseThrow(() -> ApiException.unprocessable("FRANKING_MISMATCH",
					"This doesn't match the message that was sent. Report the conversation instead."));
		return safety.report(userId, new SafetyTarget("CONNECTION", connectionId), category, details, plaintext,
				alsoBlock);
	}
}
