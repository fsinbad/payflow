package ua.sinaver.web3.payflow.service;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import ua.sinaver.web3.payflow.client.WalletClient;
import ua.sinaver.web3.payflow.dto.WalletMessage;
import ua.sinaver.web3.payflow.entity.Payment;

import java.util.List;
import java.util.stream.StreamSupport;

@Service
@Slf4j
public class WalletService {

	private final WebClient webClient;

	@Autowired
	private WalletClient walletClient;

	public WalletService(WebClient.Builder builder,
			@Value("${payflow.onchain.url}") String onchainApiUrl) {

		log.debug("Onchain API url: {}", onchainApiUrl);
		webClient = builder.baseUrl(String.format("%s/api/wallet", onchainApiUrl))
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.build();
	}

	public List<WalletMessage> calculateWallets(List<String> owners, String saltNonce) {
		val wallets = walletClient.calculateWallets(owners, saltNonce);
		/*
		 * val wallets = webClient.get()
		 * .uri(uriBuilder -> uriBuilder.path("/generate")
		 * .queryParam("owners", owners.toArray())
		 * .queryParam("nonce", saltNonce)
		 * .build())
		 * .retrieve()
		 * .bodyToFlux(WalletMessage.class)
		 * .collectList()
		 * .block();
		 */

		log.debug("Wallets: {}", wallets);
		return wallets;
	}

	// Helper to create combined request from multiple payments
	public static PaymentProcessingRequest createBatchPaymentRequest(List<Payment> payments) {
		if (payments.isEmpty()) {
			throw new IllegalArgumentException("Payments list cannot be empty");
		}

		val firstPayment = payments.get(0);
		// Validate that all payments share the same wallet session
		val sameSession = payments.stream().allMatch(payment -> payment.getWalletSession()
				.getSessionId().equals(firstPayment.getWalletSession().getSessionId()));

		if (!sameSession) {
			throw new IllegalArgumentException("All payments in batch must share the same wallet session");
		}

		return new PaymentProcessingRequest(
				firstPayment.getWalletSession().getWallet().getNetwork(),
				firstPayment.getWalletSession().getWallet().getAddress(),
				new PaymentProcessingRequest.SessionData(
						firstPayment.getWalletSession().getSessionId(),
						firstPayment.getWalletSession().getSessionKey()),
				payments.stream()
						.flatMap(payment -> StreamSupport.stream(payment.getCalls().spliterator(), false))
						.map(call -> new PaymentProcessingRequest.UserOperationCall(
								call.path("to").asText(null),
								call.path("data").asText(null),
								call.path("value").asText(null)))
						.toList());
	}

	public static PaymentProcessingRequest createPaymentRequest(Payment payment) {
		return createBatchPaymentRequest(List.of(payment));
	}

	public PaymentProcessingResponse processPayment(PaymentProcessingRequest request) {
		try {
			return walletClient.processPayment(request);
		} catch (FeignException.BadRequest e) {
			log.error("Invalid payment request: {}", request, e);
			throw e;
		} catch (FeignException e) {
			log.error("Payment processing failed: {} - {}", e.status(), request, e);
			throw e;
		}
	}

	public PaymentProcessingResponse processBatchPayment(List<Payment> payments) {
		return processPayment(createBatchPaymentRequest(payments));
	}

	public PaymentProcessingResponse processPayment(Payment payment) {
		return processPayment(createPaymentRequest(payment));
	}

	public TokenBalance getTokenBalance(String address, Integer chainId, String token) {
		/*
		 * val uriBuilder = UriComponentsBuilder.fromPath("/token/balance")
		 * .queryParam("address", address)
		 * .queryParam("chainId", chainId);
		 *
		 * if (StringUtils.isNotEmpty(token)) {
		 * uriBuilder.queryParam("token", token);
		 * }
		 *
		 * val tokenBalance = webClient.get()
		 * .uri(uriBuilder.toUriString())
		 * .retrieve()
		 * .onStatus(status -> status.equals(HttpStatus.BAD_REQUEST), response -> {
		 * log.error("Token not found for address: {} chainId: {} token: {}", address,
		 * chainId, token);
		 * return Mono.error(new RuntimeException("Token not found"));
		 * })
		 * .bodyToMono(TokenBalance.class)
		 * .onErrorResume(e -> Mono.justOrEmpty((TokenBalance) null))
		 * .block();
		 */

		try {
			val tokenBalance = walletClient.getTokenBalance(
					address,
					chainId,
					token);
			log.debug("Token balance: {}", tokenBalance);
			return tokenBalance;
		} catch (FeignException.FeignClientException e) {
			log.error("Token not found for address: {} chainId: {} token: {}", address,
					chainId, token);

			return null;
		}
	}

	public record TokenBalance(
			String balance,
			String formatted,
			String symbol,
			Integer decimals) {
	}

	public record PaymentProcessingRequest(
			Integer chainId,
			String address,
			SessionData session,
			List<UserOperationCall> calls) {
		public record SessionData(
				String sessionId,
				String sessionKey) {
		}

		public record UserOperationCall(
				String to,
				String data,
				String value) {
		}
	}

	public record PaymentProcessingResponse(
			String status,
			String txHash) {
	}
}
