package ua.sinaver.web3.payflow.controller.frames;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.sinaver.web3.payflow.entity.Payment;
import ua.sinaver.web3.payflow.entity.User;
import ua.sinaver.web3.payflow.message.FrameButton;
import ua.sinaver.web3.payflow.message.FramePaymentMessage;
import ua.sinaver.web3.payflow.message.Token;
import ua.sinaver.web3.payflow.message.farcaster.DirectCastMessage;
import ua.sinaver.web3.payflow.message.farcaster.FrameMessage;
import ua.sinaver.web3.payflow.repository.PaymentRepository;
import ua.sinaver.web3.payflow.service.*;
import ua.sinaver.web3.payflow.service.api.IIdentityService;
import ua.sinaver.web3.payflow.service.api.IUserService;
import ua.sinaver.web3.payflow.utils.FrameResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

import static ua.sinaver.web3.payflow.controller.frames.FramesController.BASE_PATH;
import static ua.sinaver.web3.payflow.controller.frames.FramesController.DEFAULT_HTML_RESPONSE;

@RestController
@RequestMapping("/farcaster/frames/pay")
@CrossOrigin(origins = "*", allowCredentials = "false")
@Transactional
@Slf4j
public class FramePaymentController {

	public static final String PAY = BASE_PATH +
			"/pay/%s";
	private static final String PAY_IN_FRAME_CONFIRM = BASE_PATH +
			"/pay/%s/frame/confirm";
	private static final String PAY_IN_FRAME_COMMENT = BASE_PATH +
			"/pay/%s/frame/comment";

	private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	@Autowired
	private FarcasterNeynarService neynarService;
	@Autowired
	private IUserService userService;
	@Value("${payflow.dapp.url}")
	private String dAppServiceUrl;
	@Value("${payflow.api.url}")
	private String apiServiceUrl;
	@Value("${payflow.frames.url}")
	private String framesServiceUrl;
	@Autowired
	private IIdentityService identityService;

	@Autowired
	private TransactionService transactionService;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private FarcasterMessagingService farcasterMessagingService;

	@Autowired
	private PaymentService paymentService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private ReceiptService receiptService;

	@Autowired
	private ContactBookService contactBookService;

	@Autowired
	private LinkService linkService;

	@PostMapping("/{identity}/frame/command")
	public ResponseEntity<?> command(@PathVariable String identity,
			@RequestBody FrameMessage frameMessage,
			@RequestParam(required = false, defaultValue = "8453") Integer chainId,
			@RequestParam(required = false, defaultValue = "usdc") String tokenId,
			@RequestParam(required = false) Double tokenAmount,
			@RequestParam(required = false) Double usdAmount) {
		log.debug("Received enter payment amount message request: {}", frameMessage);
		val validateMessage = neynarService.validaFrameRequest(
				frameMessage.trustedData().messageBytes());

		if (validateMessage == null || !validateMessage.valid()) {
			log.error("Frame message failed validation {}", validateMessage);
			return DEFAULT_HTML_RESPONSE;
		}
		log.debug("Validation frame message response {} received on url: {}  ", validateMessage,
				validateMessage.action().url());

		val senderFarcasterUser = validateMessage.action().interactor();

		User senderProfile;
		try {
			senderProfile = userService.getOrCreateUserFromFarcasterProfile(senderFarcasterUser,
					false);
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(
					new FrameResponse.FrameMessage("Missing verified identity! Contact @sinaver.eth"));
		} catch (ConstraintViolationException exception) {
			log.error("Failed to create a user for {}", senderFarcasterUser.username(), exception);
			return ResponseEntity.badRequest().body(
					new FrameResponse.FrameMessage("Identity conflict! Contact @sinaver.eth"));
		}

		val receiverFarcasterUser = validateMessage.action().cast().author() != null
				? validateMessage.action().cast().author()
				: neynarService.fetchFarcasterUser(validateMessage.action().cast().fid());

		if (receiverFarcasterUser == null) {
			log.error("Cast author information missing for: {}", validateMessage);
			return ResponseEntity.badRequest().body(
					new FrameResponse.FrameMessage("Hubs are not in sync! Missing cast author " +
							"information :("));
		}

		var token = (Token) null;
		if (tokenAmount == null && usdAmount == null) {
			val inputText = validateMessage.action().input() != null
					? validateMessage.action().input().text().toLowerCase()
					: null;

			if (StringUtils.isBlank(inputText)) {
				log.warn("Nothing entered for payment amount by {}", senderFarcasterUser);
				return ResponseEntity.badRequest().body(
						new FrameResponse.FrameMessage("Nothing entered, try again!"));
			}

			val paymentPattern = "\\s*(?<amount>\\$?[0-9]+(?:\\.[0-9]+)?[km]?)\\s*(?<rest>.*)";
			val matcher = Pattern.compile(paymentPattern, Pattern.CASE_INSENSITIVE).matcher(inputText);

			if (!matcher.find()) {
				log.warn("Enter command not recognized: {} by fid: {}", inputText, senderFarcasterUser);
				return ResponseEntity.badRequest().body(
						new FrameResponse.FrameMessage("Entered input not recognized!"));
			}

			val amountStr = matcher.group("amount");

			if (amountStr.startsWith("$")) {
				usdAmount = Double.parseDouble(amountStr.replace("$", ""));
				if (usdAmount == 0) {
					val zeroUsdAmountError = "$ amount shouldn't be ZERO!";
					log.warn(zeroUsdAmountError);
					return ResponseEntity.badRequest().body(
							new FrameResponse.FrameMessage(zeroUsdAmountError));
				}
			} else {
				tokenAmount = paymentService.parseTokenAmount(amountStr);
				if (tokenAmount == 0) {
					val zeroTokenAmountError = "Token amount shouldn't be ZERO!";
					log.warn(zeroTokenAmountError);
					return ResponseEntity.badRequest().body(
							new FrameResponse.FrameMessage(zeroTokenAmountError));
				}
			}

			val restText = matcher.group("rest");

			val tokens = paymentService.parseCommandTokens(restText);
			if (tokens.size() == 1) {
				token = tokens.getFirst();
			} else {
				val chain = paymentService.parseCommandChain(restText);
				token = tokens.stream().filter(t -> t.chain().equals(chain)).findFirst().orElse(null);
			}

			if (token == null) {
				val chainNotSupportedError = String.format("Token not supported: %s!", restText);
				log.warn(chainNotSupportedError);
				return ResponseEntity.badRequest().body(
						new FrameResponse.FrameMessage(chainNotSupportedError));
			}
		} else {
			val tokens = paymentService.parseCommandTokens(tokenId);
			if (tokens.size() == 1) {
				token = tokens.getFirst();
			} else {
				token = tokens.stream().filter(t -> t.chainId().equals(chainId)).findFirst().orElse(null);
			}

			if (token == null) {
				val chainNotSupportedError = String.format("Token not supported: %s!", tokenId);
				log.warn(chainNotSupportedError);
				return ResponseEntity.badRequest().body(
						new FrameResponse.FrameMessage(chainNotSupportedError));
			}
		}

		String paymentAddress = null;
		val paymentProfile = userService.findByUsernameOrIdentity(identity);

		if (paymentProfile != null && paymentProfile.isAllowed()) {
			paymentAddress = paymentService.getUserReceiverAddress(paymentProfile, token.chainId());
		}
		if (StringUtils.isBlank(paymentAddress)) {
			// TODO: add a proper fix to support recipients based on ens, fc name, fid
			if (identity.endsWith(".eth") || identity.endsWith(".cb.id")) {
				paymentAddress = identityService.getENSAddress(identity);
			} else {
				paymentAddress = identity;
			}
		}

		log.debug("Receiver: {}, tokenAmount: {}, usdAmount: {} , token: {}", paymentAddress,
				tokenAmount, usdAmount, token);

		val sourceApp = validateMessage.action().signer().client().displayName();
		val sourceHash = validateMessage.action().cast().hash();

		try {

			val payment = new Payment(Payment.PaymentType.FRAME,
					paymentProfile != null && paymentProfile.isAllowed() ? paymentProfile : null,
					token.chainId(), token.id());
			payment.setSender(senderProfile);
			payment.setSenderAddress(senderProfile != null ? senderProfile.getIdentity()
					: identityService
							.getHighestScoredIdentity(senderFarcasterUser.addressesWithoutCustodialIfAvailable()));
			payment.setReceiverAddress(paymentAddress);
			if (tokenAmount != null) {
				payment.setTokenAmount(tokenAmount.toString());
			} else {
				payment.setUsdAmount(usdAmount.toString());
			}
			payment.setExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
			payment.setSourceApp(sourceApp);
			// handle frame in direct cast messaging
			if (StringUtils.isNotBlank(sourceHash) && !sourceHash.equals(TokenService.ZERO_ADDRESS)) {
				val sourceRef = String.format("https://warpcast.com/%s/%s",
						receiverFarcasterUser.username(), sourceHash.substring(0, 10));
				payment.setSourceHash(sourceHash);
				payment.setSourceRef(sourceRef);
			}
			paymentRepository.saveAndFlush(payment);

			val refId = payment.getReferenceId();
			val updatedState = gson.toJson(new FramePaymentMessage(paymentAddress,
					token.chainId(), token.id(), usdAmount, tokenAmount, refId));
			val profileImage = framesServiceUrl.concat(String.format(
					"/images/profile/%s/payment.png?refId=%s",
					identity, refId));
			val frameResponseBuilder = FrameResponse.builder()
					.postUrl(apiServiceUrl.concat(String.format(PAY_IN_FRAME_CONFIRM, refId)))
					.button(new FrameButton("Quick", FrameButton.ActionType.TX,
							apiServiceUrl.concat(String.format(PAY_IN_FRAME_CONFIRM, refId))))
					.imageUrl(profileImage)
					.state(Base64.getEncoder().encodeToString(updatedState.getBytes()));

			if (senderProfile != null) {
				val paymentLink = linkService.paymentLink(payment, validateMessage, false).toString();
				frameResponseBuilder.button(new FrameButton("Advanced ⚡",
						FrameButton.ActionType.LINK,
						paymentLink));
			}
			return frameResponseBuilder.build().toHtmlResponse();
		} catch (Throwable t) {
			log.error("Something went wrong", t);
			return ResponseEntity.badRequest().body(
					new FrameResponse.FrameMessage(
							"Something went wrong! Contact @sinaver.eth \uD83D\uDC4B\uD83C\uDFFB"));
		}
	}

	@PostMapping("/{refId}/frame/confirm")
	public ResponseEntity<?> confirm(@PathVariable String refId,
			@RequestBody FrameMessage frameMessage) {
		log.debug("Received payment confirm message request: {}", frameMessage);
		val validateMessage = neynarService.validaFrameRequest(
				frameMessage.trustedData().messageBytes());

		if (validateMessage == null || !validateMessage.valid()) {
			log.error("Frame message failed validation {}", validateMessage);
			return DEFAULT_HTML_RESPONSE;
		}
		log.debug("Validation frame message response {} received on url: {}  ", validateMessage,
				validateMessage.action().url());

		val buttonIndex = validateMessage.action().tappedButton().index();
		// TODO: pass, wallet as sender which was used to execute transaction
		val transactionId = validateMessage.action().transaction() != null
				? validateMessage.action().transaction().hash()
				: null;

		val payment = paymentRepository.findByReferenceId(refId);
		if (payment == null) {
			log.error("Payment was not found for refId {}", refId);
			return ResponseEntity.badRequest().body(
					new FrameResponse.FrameMessage("Payment not found!"));
		} else if (!payment.getStatus().equals(Payment.PaymentStatus.CREATED)) {
			log.warn("Payment was completed already {} - {}", refId, payment);
			return ResponseEntity.badRequest().body(
					new FrameResponse.FrameMessage("Payment was completed already!"));
		}

		val paymentIdentity = payment.getReceiver() != null ? payment.getReceiver().getIdentity()
				: payment.getReceiverAddress();
		if (paymentIdentity != null) {
			// handle transaction execution result
			if (StringUtils.isNotBlank(transactionId)) {
				val senderAddress = validateMessage.action().address();
				log.debug("Handling tx id {} by {} for {}", transactionId, senderAddress, payment);
				// TODO: check tx execution status
				payment.setHash(transactionId);
				// update with sender address which made the transaction
				if (StringUtils.isNotBlank(senderAddress)) {
					payment.setSenderAddress(senderAddress);
				}
				payment.setStatus(Payment.PaymentStatus.COMPLETED);
				payment.setCompletedAt(Instant.now());
				if (payment.getSender() != null) {
					contactBookService.cleanContactsCache(payment.getSender());
				}

				log.debug("Updated payment for ref: {} - {}", refId, payment);

				// in case it was triggered by compose cast action, and cast info wasn't passed!
				// check if parent available, otherwise fallback to current payment frame cast
				if (StringUtils.isBlank(payment.getSourceHash())) {
					val sourceApp = validateMessage.action().signer().client().displayName();
					val sourceHash = validateMessage.action().cast().parentHash() != null
							? validateMessage.action().cast().parentHash()
							: validateMessage.action().cast().hash();
					payment.setSourceApp(sourceApp);
					if (StringUtils.isNotBlank(sourceHash) && !sourceHash.equals(TokenService.ZERO_ADDRESS)) {
						val sourceRef = validateMessage.action().cast().parentUrl() != null
								? validateMessage.action().cast().parentUrl()
								: String.format("https://warpcast.com/%s/%s",
										validateMessage.action().cast().author().username(),
										sourceHash.substring(0, 10));
						payment.setSourceHash(sourceHash);
						payment.setSourceRef(sourceRef);
					}
				}

				notificationService.notifyPaymentCompletion(payment, null);

				val profileImage = framesServiceUrl.concat(String.format("/images/profile/%s" +
						"/payment.png?refId=%s",
						paymentIdentity, refId));

				val receiptUrl = receiptService.getReceiptUrl(payment);
				return FrameResponse.builder()
						.imageUrl(profileImage)
						.textInput("Comment (64 max)")
						.button(new FrameButton("\uD83D\uDCAC Comment",
								FrameButton.ActionType.POST,
								apiServiceUrl.concat(String.format(PAY_IN_FRAME_COMMENT,
										payment.getReferenceId()))))
						.button(new FrameButton("🧾 Receipt",
								FrameButton.ActionType.LINK,
								receiptUrl))
						.button(new FrameButton("💸 History",
								FrameButton.ActionType.LINK,
								"https://warpcast.com/~/composer-action?url=https://api.alpha.payflow.me/api/farcaster/composer/pay?action=activity"))
						.build().toHtmlResponse();
			} else if (buttonIndex == 1) {
				log.debug("Handling payment through frame tx: {}", payment);
				val callData = transactionService.generateTxCallData(payment);
				log.debug("Returning callData for tx payment: {} - {}", callData, payment);
				return ResponseEntity.ok()
						.contentType(MediaType.APPLICATION_JSON)
						.body(callData);

			}
		} else {
			log.error("Frame payment message is not complete or valid: {}", payment);
		}

		return DEFAULT_HTML_RESPONSE;
	}

	@PostMapping("/{refId}/frame/comment")
	public ResponseEntity<?> comment(@PathVariable String refId,
			@RequestBody FrameMessage frameMessage) {
		log.debug("Received payment comment message request: {}", frameMessage);
		val validateMessage = neynarService.validaFrameRequest(
				frameMessage.trustedData().messageBytes());

		if (validateMessage == null || !validateMessage.valid()) {
			log.error("Frame message failed validation {}", validateMessage);
			return DEFAULT_HTML_RESPONSE;
		}
		log.debug("Validation frame message response {} received on url: {}  ", validateMessage,
				validateMessage.action().url());

		val buttonIndex = validateMessage.action().tappedButton().index();
		val senderFarcasterUser = validateMessage.action().interactor();

		val payment = paymentRepository.findByReferenceId(refId);
		if (payment == null) {
			log.error("Payment was not found for refId {}", refId);
			return DEFAULT_HTML_RESPONSE;
		} else if (!payment.getStatus().equals(Payment.PaymentStatus.COMPLETED)) {
			log.error("Payment is not in complete state {} - {}", refId, payment);
			return DEFAULT_HTML_RESPONSE;
		} else if (!StringUtils.isBlank(payment.getComment())) {
			log.error("Payment comment was added already {} - {}", refId, payment);
			return DEFAULT_HTML_RESPONSE;
		}

		val paymentIdentity = payment.getReceiver() != null ? payment.getReceiver().getIdentity()
				: payment.getReceiverAddress();
		if (paymentIdentity != null) {
			if (buttonIndex == 1) {
				log.debug("Handling add comment for payment: {}", payment);

				val profileImage = framesServiceUrl.concat(String.format("/images/profile/%s" +
						"/payment.png?refId=%s",
						paymentIdentity, refId));

				val receiptUrl = receiptService.getReceiptUrl(payment);
				val frameResponseBuilder = FrameResponse.builder()
						.imageUrl(profileImage)
						.button(new FrameButton("🧾Receipt",
								FrameButton.ActionType.LINK,
								receiptUrl))
						.button(new FrameButton("💸 History",
								FrameButton.ActionType.LINK,
								"https://warpcast.com/~/composer-action?url=https://api.alpha.payflow.me/api/farcaster/composer/pay?action=activity"))
						.state(validateMessage.action().state().serialized());

				val input = validateMessage.action().input();

				val comment = input != null ? input.text() : null;
				if (StringUtils.isNotBlank(comment) && comment.length() <= 64) {
					payment.setComment(comment);

					// send direct message with comment
					try {
						// fetch by identity, hence it will work for both dcs and feed frames
						val receiverFid = identityService.getIdentityFid(paymentIdentity);
						val receiverFname = identityService.getFarcasterUsernameByAddress(paymentIdentity);
						val senderFname = senderFarcasterUser.username();

						val messageText = String.format("""
								 @%s, you've received a comment attached to the %s %s payment by @%s 💸
								💬 Comment: %s

								%s
								🧾 Receipt: %s""",

								receiverFname,
								StringUtils.isNotBlank(payment.getTokenAmount())
										? PaymentService.formatNumberWithSuffix(payment.getTokenAmount())
										: String.format("$%s", payment.getUsdAmount()),
								payment.getToken().toUpperCase(),
								senderFname,
								payment.getComment(),
								payment.getSourceRef() != null ? String.format("🔗 Source: %s",
										payment.getSourceRef()) : "",
								receiptUrl);
						val response = farcasterMessagingService.sendMessage(
								new DirectCastMessage(receiverFid, messageText,
										UUID.randomUUID()));

						if (StringUtils.isBlank(response.result().messageId())) {
							log.error("Failed to send direct cast with {} for frame payment  " +
									"completion", messageText);
						}
					} catch (Throwable t) {
						log.error("Failed to send direct cast with exception: ", t);
					}
				} else {
					return ResponseEntity.badRequest().body(
							new FrameResponse.FrameMessage("Enter comment again (255 max)"));
				}
				return frameResponseBuilder.build().toHtmlResponse();
			}
		} else {
			log.debug("Frame payment message is not complete or valid: {}", payment);
		}

		return DEFAULT_HTML_RESPONSE;
	}
}
