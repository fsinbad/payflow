package ua.sinaver.web3.payflow.service;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ua.sinaver.web3.payflow.message.farcaster.*;
import ua.sinaver.web3.payflow.message.subscription.SubscribersMessage;
import ua.sinaver.web3.payflow.message.subscription.SubscriptionsCreatedMessage;
import ua.sinaver.web3.payflow.service.api.IFarcasterNeynarService;

import java.util.Collections;
import java.util.List;

import static ua.sinaver.web3.payflow.config.CacheConfig.NEYNAR_FARCASTER_USER_CACHE;

@Slf4j
@Service
public class FarcasterNeynarService implements IFarcasterNeynarService {

	private final WebClient neynarClient;

	public FarcasterNeynarService(WebClient.Builder builder,
	                              @Value("${payflow.hub.api.key}") String hubApiKey) {
		neynarClient = builder.baseUrl("https://api.neynar.com/v2/farcaster")
				.defaultHeader("api_key", hubApiKey)
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.build();
	}

	@Override
	public StorageUsage fetchStorageUsage(int fid) {
		log.debug("Calling Neynar Storage Usage API by fid {}", fid);
		return neynarClient.get()
				.uri(uriBuilder -> uriBuilder.path("/storage/usage")
						.queryParam("fid", fid)
						.build())
				.retrieve()
				.onStatus(HttpStatus.NOT_FOUND::equals, clientResponse -> {
					log.error("404 error when calling Neynar Storage Usage API by fid {}", fid);
					return Mono.empty();
				})
				.bodyToMono(StorageUsage.class)
				.onErrorResume(WebClientResponseException.class, e -> {
					if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
						log.debug("404 error calling Neynar Storage Usage API by fid {} - {}", fid, e.getMessage());
						return Mono.empty();
					}
					log.debug("Exception calling Neynar Storage Usage API by fid {} - {}", fid, e.getMessage());
					return Mono.error(e);
				})
				.onErrorResume(Throwable.class, e -> {
					log.debug("Exception calling Neynar Storage Usage API by fid {} - {}", fid, e.getMessage());
					return Mono.empty();
				})
				.blockOptional()
				.orElse(null);
	}

	@Override
	public StorageAllocationsResponse fetchStorageAllocations(int fid) {
		log.debug("Calling Neynar Storage Allocations API by fid {}", fid);
		return neynarClient.get()
				.uri(uriBuilder -> uriBuilder.path("/storage/allocations")
						.queryParam("fid", fid)
						.build())
				.retrieve()
				.onStatus(HttpStatus.NOT_FOUND::equals, clientResponse -> {
					log.error("404 error when calling Neynar Storage Allocations API by fid {}", fid);
					return Mono.empty();
				})
				.bodyToMono(StorageAllocationsResponse.class)
				.onErrorResume(WebClientResponseException.class, e -> {
					if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
						log.debug("404 error calling Neynar Storage Allocations API by fid {} - {}", fid,
								e.getMessage());
						return Mono.empty();
					}
					log.debug("Exception calling Neynar Storage Allocations API by fid {} - {}", fid, e.getMessage());
					return Mono.error(e);
				})
				.onErrorResume(Throwable.class, e -> {
					log.debug("Exception calling Neynar Storage Allocations API by fid {} - {}", fid, e.getMessage());
					return Mono.empty();
				})
				.blockOptional()
				.orElse(null);
	}

	@Override
	@Retryable
	public ValidatedFrameResponseMessage validateFrameMessageWithNeynar(String frameMessageInHex,
	                                                                    boolean includeChannelContext) {
		log.debug("Calling Neynar Frame Validate API for message {}",
				frameMessageInHex);
		try {
			return neynarClient.post()
					.uri("/frame/validate")
					.bodyValue(new ValidateMessageRequest(true, false, true,
							includeChannelContext, frameMessageInHex))
					.retrieve().bodyToMono(ValidatedFrameResponseMessage.class).block();
		} catch (Throwable t) {
			log.debug("Exception calling Neynar Frame Validate API: {}", t.getMessage());
			throw t;
		}
	}

	@Override
	public ValidatedFrameResponseMessage validateFrameMessageWithNeynar(String frameMessageInHex) {
		return validateFrameMessageWithNeynar(frameMessageInHex, false);
	}

	@Override
	public CastResponseMessage cast(String signer, String message, String parentHash,
	                                List<Cast.Embed> embeds) {
		log.debug("Calling Neynar Cast API with message {}",
				message);

		val response = neynarClient.post()
				.uri("/cast")
				.bodyValue(new CastRequestMessage(signer, message, parentHash,
						embeds))
				.retrieve()
				.onStatus(HttpStatus.NOT_FOUND::equals, clientResponse -> {
					log.error("404 error when calling Neynar Cast API with message {}",
							message);
					return Mono.empty();
				})
				.bodyToMono(CastResponseMessage.class)
				.onErrorResume(WebClientResponseException.class, e -> {
					if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
						log.error("404 error calling Neynar Cast API with message - {}",
								message, e);
						return Mono.empty();
					}
					log.error("Exception calling Neynar Cast API with message - {}",
							message, e);
					return Mono.error(e);
				})
				.onErrorResume(Throwable.class, e -> {
					log.error("Exception calling Neynar Cast API with message - {}",
							message, e);
					return Mono.empty();
				})
				.blockOptional()
				.orElse(null);

		log.debug("Cast response: {}", response);
		return response;
	}

	@Override
	public Cast fetchCastByHash(String hash) {
		log.debug("Calling Neynar Cast API to fetch by hash {}", hash);
		try {
			val response = neynarClient.get()
					.uri(uriBuilder -> uriBuilder.path("/cast")
							.queryParam("type", "hash")
							.queryParam("identifier", hash)
							.build())
					.retrieve().bodyToMono(CastMessageResponse.class).block();
			return response != null ? response.cast() : null;
		} catch (Throwable t) {
			log.debug("Exception calling Neynar Cast API to fetch by hash {} - {}",
					hash, t.getMessage());
			throw t;
		}
	}

	@Override
	public List<FarcasterUser> fetchTop100Followings(int fid) {
		log.debug("Calling Neynar Fetch Followings API by fid {}", fid);
		return neynarClient.get()
				.uri(uriBuilder -> uriBuilder.path("/following")
						.queryParam("fid", fid)
						.queryParam("sort_type", "algorithmic")
						.queryParam("limit", 100)
						.build())
				.retrieve()
				.onStatus(HttpStatus.NOT_FOUND::equals, clientResponse -> {
					log.error("404 error when calling Neynar Fetch Followings API by fid {}", fid);
					return Mono.empty();
				})
				.bodyToMono(FarcasterFollowingsMessage.class)
				.onErrorResume(WebClientResponseException.class, e -> {
					if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
						log.error("404 error calling Neynar Fetch Followings API by fid {} - {}",
								fid, e);
						return Mono.empty();
					}
					log.error("Exception calling Neynar Fetch Followings API by fid {} - {}",
							fid, e);
					return Mono.error(e);
				})
				.onErrorResume(Throwable.class, e -> {
					log.error("Exception calling Neynar Fetch Followings API by fid {} - {}",
							fid, e);
					return Mono.empty();
				})
				.blockOptional()
				.map(followingsMessage -> followingsMessage.users().stream()
						.map(FarcasterFollowingsMessage.FarcasterFollowing::user) // Assuming
						.toList())
				.orElse(Collections.emptyList());
	}

	@Override
	@Cacheable(value = NEYNAR_FARCASTER_USER_CACHE, unless = "#result==null")
	public FarcasterUser fetchFarcasterUser(int fid) {
		log.debug("Calling Neynar User API to fetch by fid {}", fid);
		return neynarClient.get()
				.uri(uriBuilder -> uriBuilder.path("/user/bulk")
						.queryParam("fids", fid)
						.build())
				.retrieve()
				.onStatus(HttpStatus.NOT_FOUND::equals, clientResponse -> {
					log.error("404 error when calling Neynar User API by fid {}", fid);
					return Mono.empty();
				})
				.bodyToMono(FarcasterUsersResponseMessage.class)
				.onErrorResume(WebClientResponseException.class, e -> {
					if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
						log.error("404 error calling Neynar User API by fid {} - {}",
								fid, e);
						return Mono.empty();
					}
					log.error("Exception calling Neynar User API by fid {} - {}",
							fid, e);
					return Mono.error(e);
				})
				.onErrorResume(Throwable.class, e -> {
					log.error("Exception calling Neynar User API by fid {} - {}",
							fid, e);
					return Mono.empty();
				})
				.blockOptional()
				.map(FarcasterUsersResponseMessage::users)
				.filter(users -> !users.isEmpty())
				.map(List::getFirst) // Get only the first user
				.orElse(null);
	}

	@Override
	@Cacheable(value = NEYNAR_FARCASTER_USER_CACHE, unless = "#result==null")
	public FarcasterUser fetchFarcasterUser(String custodyAddress) {
		log.debug("Calling Neynar User API to fetch by custodyAddress {}", custodyAddress);
		return neynarClient.get()
				.uri(uriBuilder -> uriBuilder.path("/user/custody-address")
						.queryParam("custody_address", custodyAddress.toLowerCase())
						.build())
				.retrieve()
				.onStatus(HttpStatus.NOT_FOUND::equals, clientResponse -> {
					log.error("404 error when calling Neynar User API by custodyAddress {}", custodyAddress);
					return Mono.empty();
				})
				.bodyToMono(FarcasterUserResponseMessage.class)
				.onErrorResume(WebClientResponseException.class, e -> {
					if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
						log.error("404 error calling Neynar User API by custodyAddress {} - {}",
								custodyAddress, e);
						return Mono.empty();
					}
					log.error("Exception calling Neynar User API by custodyAddress {} - {}",
							custodyAddress, e);
					return Mono.error(e);
				})
				.onErrorResume(Throwable.class, e -> {
					log.error("Exception calling Neynar User API by custodyAddress {} - {}",
							custodyAddress, e);
					return Mono.empty();
				})
				.blockOptional()
				.map(FarcasterUserResponseMessage::user)
				.orElse(null);
	}

	@Override
	public List<SubscriptionsCreatedMessage.Subscription> subscriptionsCreated(int fid) {
		log.debug("Calling Neynar Created Subscriptions API by fid {}", fid);
		return neynarClient.get()
				.uri(uriBuilder -> uriBuilder.path("/user/subscriptions_created")
						.queryParam("fid",/* fid == 19129 ? 576 : */fid)
						.queryParam("subscription_provider", "fabric_stp")
						.build())
				.retrieve()
				.onStatus(HttpStatus.NOT_FOUND::equals, clientResponse -> {
					log.error("404 error when calling Neynar Created Subscriptions API by fid {}", fid);
					return Mono.empty();
				})
				.bodyToMono(SubscriptionsCreatedMessage.class)
				.onErrorResume(WebClientResponseException.class, e -> {
					if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
						log.debug("404 error calling Neynar Created Subscriptions API by fid {} - {}", fid,
								e.getMessage());
						return Mono.empty();
					}
					log.debug("Exception calling Neynar Created Subscriptions API by fid {} - {}", fid, e.getMessage());
					return Mono.error(e);
				})
				.onErrorResume(Throwable.class, e -> {
					log.debug("Exception calling Neynar Created Subscriptions API by fid {} - {}", fid, e.getMessage());
					return Mono.empty();
				})
				.blockOptional()
				.map(SubscriptionsCreatedMessage::subscriptionsCreated)
				.orElse(Collections.emptyList());
	}

	@Override
	public List<SubscribersMessage.Subscriber> subscribers(int fid, boolean fabric) {
		log.debug("Calling Neynar Subscribers[{}] API by fid {}", fabric ? "Hypersub" : "Paragraph", fid);
		return neynarClient.get()
				.uri(uriBuilder -> uriBuilder.path("/user/subscribers")
						.queryParam("fid", fabric && fid == 19129 ? 576 : fid)
						.queryParam("subscription_provider", fabric ? "fabric_stp" : "paragraph")
						.build())
				.retrieve()
				.onStatus(HttpStatus.NOT_FOUND::equals, clientResponse -> {
					log.error("404 error when calling Neynar Created Subscriptions API by fid {}", fid);
					return Mono.empty();
				})
				.bodyToMono(SubscribersMessage.class)
				.onErrorResume(WebClientResponseException.class, e -> {
					if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
						log.debug("404 error calling Neynar Created Subscriptions API by fid {} - {}", fid,
								e.getMessage());
						return Mono.empty();
					}
					log.debug("Exception calling Neynar Created Subscriptions API by fid {} - {}", fid, e.getMessage());
					return Mono.error(e);
				})
				.onErrorResume(Throwable.class, e -> {
					log.debug("Exception calling Neynar Created Subscriptions API by fid {} - {}", fid, e);
					return Mono.empty();
				})
				.blockOptional()
				.map(SubscribersMessage::subscribers)
				.orElse(Collections.emptyList());
	}
}
