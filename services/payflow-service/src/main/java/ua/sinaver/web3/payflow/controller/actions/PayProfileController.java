package ua.sinaver.web3.payflow.controller.actions;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.sinaver.web3.payflow.message.CastActionMeta;
import ua.sinaver.web3.payflow.message.FrameMessage;
import ua.sinaver.web3.payflow.message.IdentityMessage;
import ua.sinaver.web3.payflow.service.api.IFarcasterHubService;
import ua.sinaver.web3.payflow.service.api.IIdentityService;
import ua.sinaver.web3.payflow.utils.FrameResponse;

import java.util.Comparator;

@RestController
@RequestMapping("/farcaster/actions/profile")
@Transactional
@Slf4j
public class PayProfileController {

	private final static CastActionMeta PAY_PROFILE_CAST_ACTION_META = new CastActionMeta(
			"Pay", "zap",
			"Use this action to pay any farcaster user whether they're on Payflow or not with in-frame txs or submit payment intent to Payflow app",
			"https://app.payflow.me/actions",
			new CastActionMeta.Action("post"));

	@Autowired
	private IFarcasterHubService farcasterHubService;
	@Autowired
	private IIdentityService identityService;

	@GetMapping
	public CastActionMeta metadata() {
		log.debug("Received metadata request for cast action: pay profile");
		return PAY_PROFILE_CAST_ACTION_META;
	}

	@PostMapping
	public ResponseEntity<?> create(@RequestBody FrameMessage castActionMessage) {
		log.debug("Received cast action: pay profile {}", castActionMessage);
		val validateMessage = farcasterHubService.validateFrameMessageWithNeynar(
				castActionMessage.trustedData().messageBytes());
		if (!validateMessage.valid()) {
			log.error("Frame message failed validation {}", validateMessage);
			return ResponseEntity.badRequest().body(
					new FrameResponse.FrameMessage("Cast action not verified!"));
		}

		log.debug("Validation frame message response {} received on url: {}  ", validateMessage,
				validateMessage.action().url());

		val casterFid = validateMessage.action().cast().fid();

		// pay first with higher social score now invite first
		val paymentAddresses = identityService.getIdentitiesInfo(casterFid)
				.stream().max(Comparator.comparingInt(IdentityMessage::score))
				.map(IdentityMessage::address).stream().toList();

		// check if profile exist
		val paymentProfile = identityService.getFidProfiles(paymentAddresses).stream().findFirst().orElse(null);
		if (paymentProfile == null) {
			log.warn("Caster fid {} is not on Payflow", casterFid);
		}

		String paymentAddress;
		if (paymentProfile == null || paymentProfile.getDefaultFlow() == null) {
			if (!paymentAddresses.isEmpty()) {
				// return first associated address
				paymentAddress = paymentAddresses.getFirst();
			} else {
				return ResponseEntity.badRequest().body(
						new FrameResponse.FrameMessage("Recipient address not found!"));
			}
		} else {
			// return profile identity
			paymentAddress = paymentProfile.getIdentity();
		}

		// just responding with dummy frame
		return ResponseEntity.ok().body(
				new FrameResponse.ActionFrame("frame", String.format("https://frames.payflow" +
						".me/%s", paymentAddress)));
	}
}
