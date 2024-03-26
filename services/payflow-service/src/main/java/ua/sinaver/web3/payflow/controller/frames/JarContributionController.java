package ua.sinaver.web3.payflow.controller.frames;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.sinaver.web3.payflow.data.Payment;
import ua.sinaver.web3.payflow.message.*;
import ua.sinaver.web3.payflow.repository.FlowRepository;
import ua.sinaver.web3.payflow.repository.PaymentRepository;
import ua.sinaver.web3.payflow.service.FlowService;
import ua.sinaver.web3.payflow.service.TransactionService;
import ua.sinaver.web3.payflow.service.XmtpValidationService;
import ua.sinaver.web3.payflow.service.api.IFarcasterHubService;
import ua.sinaver.web3.payflow.service.api.IFrameService;
import ua.sinaver.web3.payflow.service.api.IUserService;
import ua.sinaver.web3.payflow.utils.FrameResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static ua.sinaver.web3.payflow.controller.frames.FramesController.DEFAULT_HTML_RESPONSE;
import static ua.sinaver.web3.payflow.service.TransactionService.*;

@RestController
@RequestMapping("/farcaster/frames/jar")
@Transactional
@Slf4j
public class JarContributionController {

	private static final String PAY_JAR_PATH = "/api/farcaster/frames/jar/%s/contribute";
	private static final String CHOOSE_TOKEN_PATH = PAY_JAR_PATH +
			"/token";
	private static final String CHOOSE_AMOUNT_PATH = PAY_JAR_PATH +
			"/amount";
	private static final String CONFIRM_PATH = PAY_JAR_PATH +
			"/confirm";
	private static final String COMMENT_PATH = PAY_JAR_PATH +
			"/comment";
	private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	@Autowired
	private IFarcasterHubService farcasterHubService;
	@Autowired
	private IUserService userService;
	@Value("${payflow.dapp.url}")
	private String dAppServiceUrl;
	@Value("${payflow.api.url}")
	private String apiServiceUrl;
	@Value("${payflow.frames.url}")
	private String framesServiceUrl;

	@Autowired
	private IFrameService frameService;

	@Autowired
	private TransactionService transactionService;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private FlowService flowService;

	@Autowired
	private FlowRepository flowRepository;

	@Autowired
	private XmtpValidationService xmtpValidationService;

	private static String roundTokenAmount(double amount) {
		val scale = amount < 1.0 ? 5 : 1;
		val amountInDecimals = BigDecimal.valueOf(amount);
		val roundedAmount = amountInDecimals.setScale(scale, RoundingMode.HALF_UP).toString();
		log.debug("roundTokenAmount: before {} after {} with scale {}", amountInDecimals, roundedAmount, scale);
		return roundedAmount;
	}

	@PostMapping("/{uuid}/contribute")
	public ResponseEntity<String> contribute(@PathVariable String uuid,
	                                         @RequestBody FrameMessage frameMessage) {
		log.debug("Received contribute jar {} in frame message request: {}",
				uuid, frameMessage);

		ValidatedFarcasterFrameMessage validatedFarcasterMessage = null;
		ValidatedXmtpFrameMessage validatedXmtpFrameMessage = null;

		if (frameMessage.clientProtocol() != null &&
				frameMessage.clientProtocol().startsWith("xmtp")) {
			validatedXmtpFrameMessage = xmtpValidationService.validateMessage(frameMessage);
			log.debug("Validation xmtp frame message response {} received on url: {}  ",
					validatedXmtpFrameMessage,
					validatedXmtpFrameMessage.actionBody().frameUrl());

			if (StringUtils.isBlank(validatedXmtpFrameMessage.verifiedWalletAddress())) {
				log.error("Xmtp frame message failed validation (missing verifiedWalletAddress) {}",
						validatedXmtpFrameMessage);
				return DEFAULT_HTML_RESPONSE;
			}
		} else {
			validatedFarcasterMessage = farcasterHubService.validateFrameMessageWithNeynar(
					frameMessage.trustedData().messageBytes());

			log.debug("Validation farcaster frame message response {} received on url: {}  ",
					validatedFarcasterMessage,
					validatedFarcasterMessage.action().url());

			if (!validatedFarcasterMessage.valid()) {
				log.error("Frame message failed validation {}", validatedFarcasterMessage);
				return DEFAULT_HTML_RESPONSE;
			}
		}

		val jar = flowService.findJarByUUID(uuid);
		if (jar != null) {
			val jarImage = framesServiceUrl.concat(String.format("/images/jar/%s" +
					"/image.png?step=token&chainId=%s", uuid, BASE_CHAIN_ID));

			val flowWalletAddress = jar.flow().wallets().stream()
					.map(WalletMessage::address).findFirst().orElse(null);

			if (StringUtils.isBlank(flowWalletAddress)) {
				log.error("Jar doesn't support payments on chainId: {}", DEFAULT_FRAME_PAYMENTS_CHAIN_ID);
				return DEFAULT_HTML_RESPONSE;
			}

			val state = gson.toJson(new FramePaymentMessage(flowWalletAddress, DEFAULT_FRAME_PAYMENTS_CHAIN_ID, null,
					null, null));

			return FrameResponse.builder()
					.imageUrl(jarImage)
					.postUrl(apiServiceUrl.concat(String.format(CHOOSE_TOKEN_PATH, uuid)))
					//.button(new FrameButton("ETH", FrameButton.ActionType.POST, null))
					.button(new FrameButton("USDC", FrameButton.ActionType.POST, null))
					.button(new FrameButton("DEGEN", FrameButton.ActionType.POST, null))
					.state(Base64.getEncoder().encodeToString(state.getBytes()))
					.build().toHtmlResponse();
		}
		return DEFAULT_HTML_RESPONSE;
	}

	@PostMapping("/{uuid}/contribute/token")
	public ResponseEntity<String> chooseToken(@PathVariable String uuid,
	                                          @RequestBody FrameMessage frameMessage) {
		log.debug("Received choose contribution token for {} message request: {}", uuid, frameMessage);

		int buttonIndex;
		FramePaymentMessage state;

		ValidatedFarcasterFrameMessage validatedFarcasterMessage;
		ValidatedXmtpFrameMessage validatedXmtpFrameMessage;

		if (frameMessage.clientProtocol() != null &&
				frameMessage.clientProtocol().startsWith("xmtp")) {
			validatedXmtpFrameMessage = xmtpValidationService.validateMessage(frameMessage);
			log.debug("Validation xmtp frame message response {} received on url: {}  ",
					validatedXmtpFrameMessage,
					validatedXmtpFrameMessage.actionBody().frameUrl());

			if (StringUtils.isBlank(validatedXmtpFrameMessage.verifiedWalletAddress())) {
				log.error("Xmtp frame message failed validation (missing verifiedWalletAddress) {}",
						validatedXmtpFrameMessage);
				return DEFAULT_HTML_RESPONSE;
			}

			buttonIndex = validatedXmtpFrameMessage.actionBody().buttonIndex();
			state = gson.fromJson(
					new String(Base64.getDecoder().decode(validatedXmtpFrameMessage.actionBody().state())),
					FramePaymentMessage.class);

		} else {
			validatedFarcasterMessage = farcasterHubService.validateFrameMessageWithNeynar(
					frameMessage.trustedData().messageBytes());

			log.debug("Validation farcaster frame message response {} received on url: {}  ",
					validatedFarcasterMessage,
					validatedFarcasterMessage.action().url());

			if (!validatedFarcasterMessage.valid()) {
				log.error("Frame message failed validation {}", validatedFarcasterMessage);
				return DEFAULT_HTML_RESPONSE;
			}

			buttonIndex = validatedFarcasterMessage.action().tappedButton().index();
			state = gson.fromJson(
					new String(Base64.getDecoder().decode(validatedFarcasterMessage.action().state().serialized())),
					FramePaymentMessage.class);
		}

		if (state != null) {
			val token = switch (buttonIndex) {
				//case 1 -> ETH_TOKEN;
				case 1 -> USDC_TOKEN;
				case 2 -> DEGEN_TOKEN;
				default -> null;
			};

			if (token != null) {
				val updatedState = gson.toJson(new FramePaymentMessage(state.address(),
						state.chainId(), token, null, null));
				val profileImage = framesServiceUrl.concat(String.format("/images/jar/%s" +
								"/image.png?step=amount&chainId=%s&token=%s",
						uuid, BASE_CHAIN_ID, token));
				return FrameResponse.builder()
						.textInput("Enter amount, $ (1-10)")
						.postUrl(apiServiceUrl.concat(String.format(CHOOSE_AMOUNT_PATH, uuid)))
						.button(new FrameButton("$1", FrameButton.ActionType.POST, null))
						.button(new FrameButton("$3", FrameButton.ActionType.POST, null))
						.button(new FrameButton("$5", FrameButton.ActionType.POST, null))
						.button(new FrameButton("Next", FrameButton.ActionType.POST, null))
						.imageUrl(profileImage)
						.state(Base64.getEncoder().encodeToString(updatedState.getBytes()))
						.build().toHtmlResponse();
			}
		}
		return DEFAULT_HTML_RESPONSE;
	}

	@PostMapping("/{uuid}/contribute/amount")
	public ResponseEntity<String> chooseAmount(@PathVariable String uuid,
	                                           @RequestBody FrameMessage frameMessage) {
		log.debug("Received enter contribution amount message request: {}", frameMessage);

		int buttonIndex;
		FramePaymentMessage state;
		String inputText;
		List<String> clickedProfileAddresses;

		String sourceApp;
		String sourceRef;

		ValidatedFarcasterFrameMessage validatedFarcasterMessage;
		ValidatedXmtpFrameMessage validatedXmtpFrameMessage;

		if (frameMessage.clientProtocol() != null &&
				frameMessage.clientProtocol().startsWith("xmtp")) {
			validatedXmtpFrameMessage = xmtpValidationService.validateMessage(frameMessage);
			log.debug("Validation xmtp frame message response {} received on url: {}  ",
					validatedXmtpFrameMessage,
					validatedXmtpFrameMessage.actionBody().frameUrl());

			if (StringUtils.isBlank(validatedXmtpFrameMessage.verifiedWalletAddress())) {
				log.error("Xmtp frame message failed validation (missing verifiedWalletAddress) {}",
						validatedXmtpFrameMessage);
				return DEFAULT_HTML_RESPONSE;
			}
			clickedProfileAddresses = Collections.singletonList(validatedXmtpFrameMessage.verifiedWalletAddress());
			buttonIndex = validatedXmtpFrameMessage.actionBody().buttonIndex();
			inputText = validatedXmtpFrameMessage.actionBody().inputText();
			sourceApp = "Xmtp";
			sourceRef = null; // maybe link the chat of the user who paid? or conversation?
			state = gson.fromJson(
					new String(Base64.getDecoder().decode(validatedXmtpFrameMessage.actionBody().state())),
					FramePaymentMessage.class);
		} else {
			validatedFarcasterMessage = farcasterHubService.validateFrameMessageWithNeynar(
					frameMessage.trustedData().messageBytes());

			log.debug("Validation farcaster frame message response {} received on url: {}  ",
					validatedFarcasterMessage,
					validatedFarcasterMessage.action().url());

			if (!validatedFarcasterMessage.valid()) {
				log.error("Frame message failed validation {}", validatedFarcasterMessage);
				return DEFAULT_HTML_RESPONSE;
			}

			clickedProfileAddresses = frameService.getFidAddresses(
					validatedFarcasterMessage.action().interactor().fid());
			buttonIndex = validatedFarcasterMessage.action().tappedButton().index();
			inputText = validatedFarcasterMessage.action().input() != null ?
					validatedFarcasterMessage.action().input().text() : null;

			sourceApp = validatedFarcasterMessage.action().signer().client().displayName();
			val casterFid = validatedFarcasterMessage.action().cast().fid();
			val casterFcName = frameService.getFidFname(casterFid);
			// maybe would make sense to reference top cast instead (if it's a bot cast)
			sourceRef = String.format("https://warpcast.com/%s/%s",
					casterFcName, validatedFarcasterMessage.action().cast().hash().substring(0,
							10));
			state = gson.fromJson(
					new String(Base64.getDecoder().decode(validatedFarcasterMessage.action().state().serialized())),
					FramePaymentMessage.class);
		}

		val profiles = frameService.getFidProfiles(clickedProfileAddresses);

		val jar = flowService.findJarByUUID(uuid);
		if (jar != null && state != null) {
			log.debug("Previous payment state: {}", state);

			var usdAmount = switch (buttonIndex) {
				case 1 -> 1.0;
				case 2 -> 3.0;
				case 3 -> 5.0;
				case 4 -> {
					if (inputText == null) {
						yield null;
					}
					try {
						val parsedAmount = Double.parseDouble(inputText);
						if (parsedAmount > 0 && parsedAmount <= 10.0) {
							yield parsedAmount;
						} else {
							log.error("Parsed input token amount {} is not within the valid range" +
									" (1-10)", parsedAmount);
							yield null;
						}
					} catch (NumberFormatException ignored) {
						log.error("Failed to parse input token amount.");
						yield null;
					}
				}
				default -> null;
			};

			// fallback to previous state
			if (usdAmount == null && state.usdAmount() != null) {
				usdAmount = state.usdAmount();
			}

			if (usdAmount != null && buttonIndex == 4) {

				val tokenAmount = roundTokenAmount(
						usdAmount / transactionService.getPrice(state.token()));

				val profile = userService.findByIdentity(jar.profile().identity());
				val payment = new Payment(Payment.PaymentType.FRAME, profile,
						state.chainId(), state.token());

				// TODO: refactor to fetch jar data object instead of message
				val flow = flowRepository.findByUuid(uuid);
				payment.setReceiverFlow(flow);

				payment.setUsdAmount(usdAmount.toString());
				payment.setSourceApp(sourceApp);
				payment.setSourceRef(sourceRef);

				paymentRepository.save(payment);

				val refId = payment.getReferenceId();
				val updatedState = gson.toJson(new FramePaymentMessage(state.address(),
						state.chainId(), state.token(), usdAmount, refId));
				val jarImage = framesServiceUrl.concat(String.format("/images/jar/%s" +
								"/image.png?step=confirm&chainId=%s&token=%s&usdAmount=%s&amount=%s",
						uuid, state.chainId(), state.token(), usdAmount, tokenAmount));
				val frameResponseBuilder = FrameResponse.builder()
						.postUrl(apiServiceUrl.concat(String.format(CONFIRM_PATH, uuid)))
						.button(new FrameButton("Pay now", FrameButton.ActionType.TX,
								apiServiceUrl.concat(String.format(CONFIRM_PATH, uuid))))
						.imageUrl(jarImage)
						.state(Base64.getEncoder().encodeToString(updatedState.getBytes()));

				// for now just check if profile exists
				if (!profiles.isEmpty()) {
					frameResponseBuilder.button(new FrameButton("Pay later \uD83D\uDD51",
							FrameButton.ActionType.POST,
							apiServiceUrl.concat(String.format(CONFIRM_PATH, uuid))));
				}

				return frameResponseBuilder.build().toHtmlResponse();
			} else {
				val updatedState = gson.toJson(new FramePaymentMessage(state.address(),
						state.chainId(), state.token(), usdAmount, null));

				val tokenAmount = usdAmount != null ? roundTokenAmount(
						usdAmount / transactionService.getPrice(state.token())) : "";

				val jarImage = framesServiceUrl.concat(String.format("/images/jar/%s" +
								"/image.png?step=amount&chainId=%s&token=%s&usdAmount=%s&amount=%s",
						uuid, state.chainId(), state.token(),
						usdAmount != null ? usdAmount : "", tokenAmount));
				return FrameResponse.builder()
						.textInput(String.format("Enter amount%s, $ (1-10)", usdAmount == null ?
								" again" : ""))
						.postUrl(apiServiceUrl.concat(String.format(CHOOSE_AMOUNT_PATH,
								uuid)))
						.button(new FrameButton("$1", FrameButton.ActionType.POST, null))
						.button(new FrameButton("$3", FrameButton.ActionType.POST, null))
						.button(new FrameButton("$5", FrameButton.ActionType.POST, null))
						.button(new FrameButton("Next", FrameButton.ActionType.POST, null))
						.imageUrl(jarImage)
						.state(Base64.getEncoder().encodeToString(updatedState.getBytes()))
						.build().toHtmlResponse();
			}
		}
		return DEFAULT_HTML_RESPONSE;
	}

	@PostMapping("/{uuid}/contribute/confirm")
	public ResponseEntity<String> confirm(@PathVariable String uuid,
	                                      @RequestBody FrameMessage frameMessage) {
		log.debug("Received contribution confirm message request: {}", frameMessage);
		int buttonIndex;
		FramePaymentMessage paymentState;
		List<String> clickedProfileAddresses;
		String transactionId = null;
		String state;

		ValidatedFarcasterFrameMessage validatedFarcasterMessage;
		ValidatedXmtpFrameMessage validatedXmtpFrameMessage;

		if (frameMessage.clientProtocol() != null &&
				frameMessage.clientProtocol().startsWith("xmtp")) {
			validatedXmtpFrameMessage = xmtpValidationService.validateMessage(frameMessage);
			log.debug("Validation xmtp frame message response {} received on url: {}  ",
					validatedXmtpFrameMessage,
					validatedXmtpFrameMessage.actionBody().frameUrl());

			if (StringUtils.isBlank(validatedXmtpFrameMessage.verifiedWalletAddress())) {
				log.error("Xmtp frame message failed validation (missing verifiedWalletAddress) {}",
						validatedXmtpFrameMessage);
				return DEFAULT_HTML_RESPONSE;
			}
			clickedProfileAddresses = Collections.singletonList(validatedXmtpFrameMessage.verifiedWalletAddress());
			buttonIndex = validatedXmtpFrameMessage.actionBody().buttonIndex();
			state = validatedXmtpFrameMessage.actionBody().state();
		} else {
			validatedFarcasterMessage = farcasterHubService.validateFrameMessageWithNeynar(
					frameMessage.trustedData().messageBytes());

			log.debug("Validation farcaster frame message response {} received on url: {}  ",
					validatedFarcasterMessage,
					validatedFarcasterMessage.action().url());

			if (!validatedFarcasterMessage.valid()) {
				log.error("Frame message failed validation {}", validatedFarcasterMessage);
				return DEFAULT_HTML_RESPONSE;
			}

			clickedProfileAddresses = frameService.getFidAddresses(
					validatedFarcasterMessage.action().interactor().fid());
			buttonIndex = validatedFarcasterMessage.action().tappedButton().index();
			state = validatedFarcasterMessage.action().state().serialized();
			transactionId = validatedFarcasterMessage.action().transaction() != null ?
					validatedFarcasterMessage.action().transaction().hash() : null;
		}

		paymentState = gson.fromJson(
				new String(Base64.getDecoder().decode(state)),
				FramePaymentMessage.class);

		val profiles = frameService.getFidProfiles(clickedProfileAddresses);

		val jar = flowService.findJarByUUID(uuid);

		if (jar != null && state != null) {
			log.debug("Previous payment state: {}", state);
			if (isFramePaymentMessageComplete(paymentState)) {
				val flowWalletAddress = jar.flow().wallets().stream()
						.filter(wallet -> wallet.network() == paymentState.chainId())
						.map(WalletMessage::address).findFirst().orElse(null);

				if (!paymentState.address().equals(flowWalletAddress)) {
					log.error("Transaction address doesn't match jar's wallet address: " +
							"{} vs {}", paymentState.address(), flowWalletAddress);
					return DEFAULT_HTML_RESPONSE;
				}

				val refId = paymentState.refId();
				val payment = paymentRepository.findByReferenceId(refId);
				if (payment == null) {
					log.error("Payment was not found for refId {}", refId);
					return DEFAULT_HTML_RESPONSE;
				} else if (!payment.getStatus().equals(Payment.PaymentStatus.PENDING)) {
					log.error("Payment is not in pending state {} - {}", refId, payment);
					return DEFAULT_HTML_RESPONSE;
				}

				// handle transaction execution result
				if (!StringUtils.isBlank(transactionId)) {
					log.debug("Handling tx id {} for {}", transactionId, state);
					// TODO: check tx execution status
					payment.setHash(transactionId);
					payment.setStatus(Payment.PaymentStatus.COMPLETED);
					payment.setCompletedDate(new Date());

					log.debug("Updated payment for ref: {} - {}", refId, payment);

					val tokenAmount = roundTokenAmount(
							paymentState.usdAmount() / transactionService.getPrice(paymentState.token()));
					val jarImage = framesServiceUrl.concat(String.format("/images/jar/%s" +
									"/image.png?step=execute&chainId=%s&token=%s&usdAmount=%s" +
									"&amount=%s&status=%s", uuid, paymentState.chainId(), paymentState.token(),
							paymentState.usdAmount(), tokenAmount, "success"));
					return FrameResponse.builder()
							.imageUrl(jarImage)
							.textInput("Enter your comment")
							.button(new FrameButton("\uD83D\uDCAC Add comment",
									FrameButton.ActionType.POST,
									apiServiceUrl.concat(String.format(COMMENT_PATH, uuid))))
							.button(new FrameButton("\uD83D\uDD0E Check tx details",
									FrameButton.ActionType.LINK,
									"https://basescan.org/tx/" + transactionId))
							.state(state)
							.build().toHtmlResponse();
				} else if (buttonIndex == 1) {
					log.debug("Handling payment through frame tx: {}", state);
					val callData = transactionService.generateTxCallData(paymentState);
					log.debug("Returning callData for tx payment: {} - {}", callData, state);
					return ResponseEntity.ok()
							.contentType(MediaType.APPLICATION_JSON)
							.body(callData);

				} else if (buttonIndex == 2) {
					log.debug("Submitting payment for later in app execution: {}", state);
					// TODO: for now set the first
					payment.setSender(profiles.getFirst());
					payment.setType(Payment.PaymentType.INTENT);
					val tokenAmount = roundTokenAmount(
							paymentState.usdAmount() / transactionService.getPrice(paymentState.token()));
					val jarImage = framesServiceUrl.concat(String.format("/images/jar/%s" +
									"/image.png?step=execute&chainId=%s&token=%s&usdAmount=%s" +
									"&amount=%s",
							uuid, paymentState.chainId(), paymentState.token(),
							paymentState.usdAmount(), tokenAmount));
					return FrameResponse.builder()
							.imageUrl(jarImage)
							.button(new FrameButton("\uD83D\uDCF1 Payflow",
									FrameButton.ActionType.LINK, dAppServiceUrl))
							.build().toHtmlResponse();
				}
			} else {
				log.error("Frame payment message is not complete or valid: {}", state);
			}
		}

		return DEFAULT_HTML_RESPONSE;
	}

	@PostMapping("/{uuid}/contribute/comment")
	public ResponseEntity<String> comment(@PathVariable String uuid,
	                                      @RequestBody FrameMessage frameMessage) {
		log.debug("Received contribution comment message request: {}", frameMessage);
		val validateMessage =
				farcasterHubService.validateFrameMessageWithNeynar(
						frameMessage.trustedData().messageBytes());

		if (!validateMessage.valid()) {
			log.error("Frame message failed validation {}", validateMessage);
			return DEFAULT_HTML_RESPONSE;
		}
		log.debug("Validation frame message response {} received on url: {}  ", validateMessage,
				validateMessage.action().url());

		val buttonIndex = validateMessage.action().tappedButton().index();
		val jar = flowService.findJarByUUID(uuid);
		var state = gson.fromJson(
				new String(Base64.getDecoder().decode(validateMessage.action().state().serialized())),
				FramePaymentMessage.class);

		if (jar != null && state != null) {
			log.debug("Previous payment state: {}", state);

			if (isFramePaymentMessageComplete(state)) {
				val refId = state.refId();
				val payment = paymentRepository.findByReferenceId(refId);
				if (payment == null) {
					log.error("Payment was not found for refId {}", refId);
					return DEFAULT_HTML_RESPONSE;
				}

				if (buttonIndex == 1 && payment.getStatus().equals(Payment.PaymentStatus.COMPLETED) &&
						StringUtils.isBlank(payment.getComment())) {
					log.debug("Handling add comment for payment: {}", payment);
					// TODO: optimize
					val tokenAmount = roundTokenAmount(
							state.usdAmount() / transactionService.getPrice(state.token()));
					val jarImage = framesServiceUrl.concat(String.format("/images/jar/%s" +
									"/image.png?step=execute&chainId=%s&token=%s&usdAmount=%s" +
									"&amount=%s&status=%s",
							uuid, state.chainId(), state.token(),
							state.usdAmount(), tokenAmount, "success"));

					val frameResponseBuilder = FrameResponse.builder()
							.imageUrl(jarImage)
							.button(new FrameButton("\uD83D\uDD0E Check tx details",
									FrameButton.ActionType.LINK,
									"https://basescan.org/tx/" + payment.getHash()))
							.state(validateMessage.action().state().serialized());

					val input = validateMessage.action().input();

					val comment = input != null ? input.text() : null;
					if (!StringUtils.isBlank(comment)) {
						payment.setComment(comment);
					} else {
						frameResponseBuilder.textInput("Enter your comment again")
								.button(new FrameButton("\uD83D\uDCAC Add comment",
										FrameButton.ActionType.POST,
										apiServiceUrl.concat(String.format(COMMENT_PATH, uuid))));
					}
					return frameResponseBuilder.build().toHtmlResponse();
				}
			} else {
				log.debug("Frame payment message is not complete or valid: {}", state);
			}
		}

		return DEFAULT_HTML_RESPONSE;
	}
}
