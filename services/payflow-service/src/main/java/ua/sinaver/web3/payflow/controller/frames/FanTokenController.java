package ua.sinaver.web3.payflow.controller.frames;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.sinaver.web3.payflow.config.PayflowConfig;
import ua.sinaver.web3.payflow.data.Payment;
import ua.sinaver.web3.payflow.data.User;
import ua.sinaver.web3.payflow.graphql.generated.types.SocialDappName;
import ua.sinaver.web3.payflow.message.farcaster.FrameMessage;
import ua.sinaver.web3.payflow.message.farcaster.ValidatedFrameResponseMessage;
import ua.sinaver.web3.payflow.message.moxie.FanToken;
import ua.sinaver.web3.payflow.repository.PaymentRepository;
import ua.sinaver.web3.payflow.service.*;
import ua.sinaver.web3.payflow.utils.FrameResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static ua.sinaver.web3.payflow.controller.frames.FramesController.DEFAULT_HTML_RESPONSE;
import static ua.sinaver.web3.payflow.service.TokenService.BASE_CHAIN_ID;

@RestController
@RequestMapping("/farcaster/frames/fan")
@CrossOrigin(origins = "*", allowCredentials = "false")
@Transactional
@Slf4j
public class FanTokenController {
	@Autowired
	private FarcasterNeynarService neynarService;
	@Autowired
	private PaymentRepository paymentRepository;
	@Autowired
	private PayflowConfig payflowConfig;
	@Autowired
	private UserService userService;
	@Autowired
	private IdentityService identityService;
	@Autowired
	private LinkService linkService;
	@Autowired
	private FanTokenService fanTokenService;

	private static Payment getFanTokenPayment(ValidatedFrameResponseMessage validateMessage,
	                                          User user,
	                                          Integer receiverFid,
	                                          String receiverAddress,
	                                          FanToken fanToken) {
		val sourceApp = validateMessage.action().signer().client().displayName();
		val castHash = validateMessage.action().cast().hash();
		val sourceRef = String.format("https://warpcast.com/%s/%s",
				validateMessage.action().cast().author().username(), castHash.substring(0, 10));

		val payment = new Payment(Payment.PaymentType.INTENT, null, BASE_CHAIN_ID,
				String.format("%s;%s", fanToken.name(), fanToken.subject().subjectAddress()));
		payment.setCategory("fan");
		payment.setReceiverFid(receiverFid);
		payment.setReceiverAddress(receiverAddress);
		payment.setSender(user);
		payment.setSourceApp(sourceApp);
		payment.setSourceRef(sourceRef);
		payment.setSourceHash(castHash);
		return payment;
	}

	@PostMapping("/{name}/submit")
	public ResponseEntity<?> submit(@RequestBody FrameMessage frameMessage,
	                                @PathVariable String name) {
		log.debug("Received submit buy fan token {} message request: {}", name, frameMessage);
		val validateMessage = neynarService.validateFrameMessageWithNeynar(
				frameMessage.trustedData().messageBytes());

		if (!validateMessage.valid()) {
			log.error("Frame message failed validation {}", validateMessage);
			return DEFAULT_HTML_RESPONSE;
		}
		log.debug("Validation frame message response {} received on url: {}  ", validateMessage,
				validateMessage.action().url());

		val interactor = validateMessage.action().interactor();

		User clickedProfile;
		try {
			clickedProfile = userService.getOrCreateUserFromFarcasterProfile(interactor,
					false);
		} catch (IllegalArgumentException exception) {
			return ResponseEntity.badRequest().body(
					new FrameResponse.FrameMessage("Missing verified identity! Contact @sinaver.eth"));
		} catch (ConstraintViolationException exception) {
			log.error("Failed to create a user for {}", interactor.username(), exception);
			return ResponseEntity.badRequest().body(
					new FrameResponse.FrameMessage("Identity conflict! Contact @sinaver.eth"));
		}

		if (clickedProfile == null) {
			log.error("Clicked fid {} is not on payflow", interactor);
			return ResponseEntity.badRequest().body(
					new FrameResponse.FrameMessage("Sign up on Payflow first!"));
		}

		val fanToken = fanTokenService.getFanToken(name.replace("channel:", "/"));
		if (fanToken == null) {
			log.error("Fan token not found: {}", name);
			return ResponseEntity.badRequest().body(
					new FrameResponse.FrameMessage(String.format("`%s` fan token not found!",
							name)));
		}

		val recipientTexts = Optional.ofNullable(validateMessage.action().input())
				.map(input -> input.text().trim())
				.filter(text -> !text.isEmpty())
				.map(text -> text.toLowerCase().replaceAll("@", ""))
				.map(text -> text.toLowerCase().split("[,\\s]+"))
				.map(Arrays::asList)
				.orElse(Collections.singletonList(""));

		val payments = new ArrayList<Payment>();
		for (val recipientText : recipientTexts) {
			try {
				Integer receiverFid;
				String receiverAddress;
				if (StringUtils.isNotBlank(recipientText)) {
					val addresses = identityService.getFnameAddresses(recipientText);
					val addressesWithMoxiePass = fanTokenService.filterByMoxiePassHolders(addresses);
					if (addressesWithMoxiePass.isEmpty()) {
						log.error("No moxie pass found for: {}", recipientText);
						continue;
					}
					val identity = identityService.getHighestScoredIdentityInfo(addressesWithMoxiePass);
					if (identity == null) {
						log.error("Farcaster user identity not found: {}", recipientText);
						continue;
					}
					receiverFid = identity.meta().socials().stream()
							.filter(s -> SocialDappName.farcaster.name().equals(s.dappName()))
							.map(s -> {
								try {
									return Integer.parseInt(s.profileId());
								} catch (NumberFormatException e) {
									return null;
								}
							})
							.findFirst()
							.orElse(null);
					if (receiverFid == null) {
						log.error("Farcaster user fid not found: {}", recipientText);
						continue;
					}
					receiverAddress = identity.address();
				} else {
					receiverFid = interactor.fid();
					val addressesWithMoxiePass = fanTokenService
							.filterByMoxiePassHolders(interactor.addressesWithoutCustodialIfAvailable());
					if (addressesWithMoxiePass.isEmpty()) {
						log.error("No moxie pass found for: {}", recipientText);
						return ResponseEntity.badRequest().body(
								new FrameResponse.FrameMessage("Moxie pass required"));
					}
					val identity = identityService.getHighestScoredIdentityInfo(addressesWithMoxiePass);
					if (identity == null) {
						log.error("Farcaster user identity not found: {}", recipientText);
						return ResponseEntity.badRequest().body(
								new FrameResponse.FrameMessage("Farcaster identity is missing"));
					}
					receiverAddress = identity.address();
				}
				val payment = getFanTokenPayment(validateMessage, clickedProfile, receiverFid,
						receiverAddress, fanToken);
				payments.add(payment);
				log.debug("Mint payment intent saved: {}", payment);
			} catch (Exception e) {
				log.error("Error processing recipient: {}", recipientText, e);
			}
		}

		if (payments.isEmpty()) {
			return ResponseEntity.badRequest().body(
					new FrameResponse.FrameMessage("No eligible username found"));
		}

		paymentRepository.saveAll(payments);
		log.debug("Mint payment intents saved: {}", payments);

		val paymentLink = linkService.paymentLink(payments.getFirst(), validateMessage, false);
		log.debug("Redirecting to {}", paymentLink);
		return ResponseEntity.status(HttpStatus.FOUND).location(paymentLink).build();
	}
}
