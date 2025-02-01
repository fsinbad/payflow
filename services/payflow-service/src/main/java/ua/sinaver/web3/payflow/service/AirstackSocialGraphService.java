package ua.sinaver.web3.payflow.service;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.GraphQlClient;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ua.sinaver.web3.payflow.graphql.generated.types.*;
import ua.sinaver.web3.payflow.message.ConnectedAddresses;
import ua.sinaver.web3.payflow.message.moxie.FanToken;
import ua.sinaver.web3.payflow.message.moxie.FanTokenHolder;
import ua.sinaver.web3.payflow.message.moxie.FanTokenLockWallet;
import ua.sinaver.web3.payflow.service.api.ISocialGraphService;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static ua.sinaver.web3.payflow.config.CacheConfig.*;

@Service
@Slf4j
public class AirstackSocialGraphService implements ISocialGraphService {

	private final GraphQlClient airstackGraphQlClient;
	private final GraphQlClient moxieStatsGraphQlClient;
	private final GraphQlClient moxieVestingGraphQlClient;
	private final GraphQlClient moxieProtocolGraphQlClient;

	public AirstackSocialGraphService(WebClient.Builder builder,
			@Value("${payflow.airstack.api.url}") String airstackUrl,
			@Value("${payflow.airstack.api.key}") String airstackApiKey,
			@Value("${payflow.moxie.subgraph.url}") String moxieSubgraphUrl,
			@Value("${payflow.moxie.api.url}") String moxieUrl,
			@Value("${payflow.moxie.api.key}") String moxieApiKey) {
		val airstackWebClient = builder
				.baseUrl(airstackUrl)
				.build();

		val moxieSubgraphWebClient = builder.build();
		val moxieProtocolWebClient = builder.build();

		airstackGraphQlClient = HttpGraphQlClient.builder(airstackWebClient)
				.header(HttpHeaders.AUTHORIZATION, airstackApiKey)
				.build();

		moxieStatsGraphQlClient = HttpGraphQlClient.builder(moxieSubgraphWebClient)
				.url(moxieSubgraphUrl.concat("/moxie_protocol_stats_mainnet/version/latest"))
				.build();

		moxieVestingGraphQlClient = HttpGraphQlClient.builder(moxieSubgraphWebClient)
				.url(moxieSubgraphUrl.concat("/moxie_vesting_mainnet/version/latest"))
				.build();

		moxieProtocolGraphQlClient = HttpGraphQlClient.builder(moxieProtocolWebClient)
				.url(moxieUrl)
				.header("x-airstack-protocol", moxieApiKey)
				.build();
	}

	@Override
	public FarcasterCast getTopCastReply(String parentHash, List<String> ignoredFids) {
		try {
			val replies = airstackGraphQlClient.documentName("getCastRepliesSocialCapitalValue")
					.variable("parentHash", parentHash)
					.execute().block();
			if (replies != null) {
				val topReply = replies.field("FarcasterReplies.Reply")
						.toEntityList(FarcasterCast.class).stream()
						.filter(reply -> reply.getSocialCapitalValue() != null && !ignoredFids.contains(reply.getFid()))
						.max(Comparator.comparingDouble(reply -> reply.getSocialCapitalValue().getFormattedValue()))
						.orElse(null);
				log.debug("Top cast reply: {} for hash: {}", topReply, parentHash);
				return topReply;
			}
		} catch (Throwable t) {
			log.error("Error during fetching top cast reply for hash: {}, error: {} - {}",
					parentHash,
					t.getMessage(),
					log.isTraceEnabled() ? t : null);
		}

		log.warn("Failed to fetch top cast reply for hash: {} ", parentHash);
		return null;

	}

	@Override
	public List<FarcasterFanTokenAuction> getFanTokenAuctions(List<String> farcasterUsernames) {
		try {
			val auctionsResponse = airstackGraphQlClient.documentName("getFanTokenAuctionsForContacts")
					.variable("statuses", List.of("UPCOMING", "ACTIVE"))
					.variable("entityNames", farcasterUsernames)
					.execute().block();
			if (auctionsResponse != null) {

				log.debug("Response: {}", auctionsResponse);
				val auctions = auctionsResponse.field("FarcasterFanTokenAuctions.FarcasterFanTokenAuction")
						.toEntityList(FarcasterFanTokenAuction.class).stream().toList();
				log.debug("Fetched fan tokens {} for farcaster usernames: {}", auctions,
						farcasterUsernames);
				return auctions;
			}
		} catch (Throwable t) {
			log.error("Error during fetching fan tokens for farcaster usernames: {}, error: {} - {}",
					farcasterUsernames,
					t.getMessage(),
					log.isTraceEnabled() ? t : null);
		}

		log.warn("Failed to fetch fan tokens for farcaster usernames: {}", farcasterUsernames);
		return Collections.emptyList();
	}

	@Override
	public FanToken getFanToken(String name) {
		try {
			val response = moxieStatsGraphQlClient.documentName("getFanToken")
					.variable("name", name)
					.execute().block();
			if (response != null) {
				log.debug("Response: {}", response);
				val subjectTokens = response.field("subjectTokens")
						.toEntityList(FanToken.class);
				if (!subjectTokens.isEmpty()) {
					val fanToken = subjectTokens.getFirst();
					log.debug("Fetched fan token: {}", fanToken);
					return fanToken;
				}
			}
		} catch (Throwable t) {
			log.error("Error during fetching fan token for name: {}, error: {} - {}",
					name,
					t.getMessage(),
					log.isTraceEnabled() ? t : null);
		}

		log.warn("Failed to fetch fan token for name: {}", name);
		return null;
	}

	@Override
	public boolean hasMoxiePass(String address) {
		try {
			val response = moxieProtocolGraphQlClient.documentName("hasMoxiePass")
					.variable("address", address)
					.execute().block();
			if (response != null) {
				log.debug("Response: {}", response);
				val status = response.field("FarcasterMoxieMintStatus.status").toEntity(String.class);
				if ("COMPLETED".equals(status)) {
					log.debug("Moxie Pass status for address {}: {}", address, status);
					return true;
				}
			}
		} catch (Throwable t) {
			log.error("Error during checking Moxie Pass for address: {}, error: {} - {}",
					address,
					t.getMessage(),
					log.isTraceEnabled() ? t : null);
		}

		log.warn("Failed to check Moxie Pass for address: {}", address);
		return false;
	}

	@Override
	public List<String> getFanTokenHolders(String fanTokenName) {
		try {
			val holdersResponse = moxieStatsGraphQlClient.documentName("getFanTokenHolders")
					.variable("fanTokenName", fanTokenName)
					.execute().block();
			if (holdersResponse != null) {
				log.debug("Response: {}", holdersResponse);
				val holders = holdersResponse.field("subjectTokens")
						.toEntityList(FanTokenHolder.class).stream()
						.flatMap(subjectToken -> subjectToken.portfolio().stream())
						.map(portfolio -> portfolio.user().id())
						.distinct()
						.toList();
				log.debug("Fetched fan token holders {} for token name: {}", holders,
						fanTokenName);

				val lockWalletsResponse = moxieVestingGraphQlClient.documentName(
						"getFanTokenVestingContractBeneficiary")
						.variable("addresses", holders)
						.execute().block();

				if (lockWalletsResponse != null) {
					val lockWalletsMap = lockWalletsResponse.field("tokenLockWallets")
							.toEntityList(FanTokenLockWallet.class)
							.stream().collect(Collectors.toMap(
									FanTokenLockWallet::address,
									FanTokenLockWallet::beneficiary));

					return holders.stream().map(holder -> lockWalletsMap.getOrDefault(holder,
							holder)).collect(Collectors.toList());
				}
			}
		} catch (Throwable t) {
			log.error("Error during fetching fan token holders for token name: {}, " +
					"error: {} - {}",
					fanTokenName,
					t.getMessage(),
					log.isTraceEnabled() ? t : null);
		}

		log.warn("Failed to fetch fan token holders for token name: {}", fanTokenName);
		return Collections.emptyList();
	}

	@Override
	public FarcasterCast getReplySocialCapitalValue(String hash) {
		try {
			val replies = airstackGraphQlClient.documentName("getReplySocialCapitalValue")
					.variable("hash", hash)
					.execute().block();
			if (replies != null) {
				val reply = replies.field("FarcasterReplies.Reply")
						.toEntityList(FarcasterCast.class).stream()
						.findFirst().orElse(null);

				if (reply != null && reply.getSocialCapitalValue() != null) {
					log.debug("Reply SCV: {} for url: {}",
							reply.getSocialCapitalValue().getFormattedValue(), hash);
					return reply;
				}

			}
		} catch (Throwable t) {
			log.error("Error during fetching reply SCV for hash: {}, error: {} - {}",
					hash,
					t.getMessage(),
					log.isTraceEnabled() ? t : null);
		}

		log.warn("Failed to fetch reply SCV for hash: {} ", hash);
		return null;

	}

	@Override
	@CacheEvict(value = FARCASTER_VERIFICATIONS_CACHE_NAME)
	public void cleanIdentityVerifiedAddressesCache(String identity) {
		log.debug("Cleared verification cache for {}", identity);
	}

	@Override
	@Cacheable(value = FARCASTER_VERIFICATIONS_CACHE_NAME, unless = "#result==null")
	public ConnectedAddresses getIdentityVerifiedAddresses(String identity) {
		val verifiedAddressesResponse = airstackGraphQlClient.documentName("getFarcasterVerifications")
				.variable("identity", identity)
				.execute().block();

		if (verifiedAddressesResponse == null) {
			log.error("No connected addresses for {}", identity);
			return null;
		}

		val verifiedAddresses = verifiedAddressesResponse.field("Socials.Social")
				.toEntityList(Social.class).stream()
				.limit(1).findFirst()
				.map(s -> new ConnectedAddresses(s.getUserAddress(),
						s.getConnectedAddresses().stream()
								.map(ConnectedAddress::getAddress)
								.filter(address -> address.startsWith("0x"))
								.toList()))
				.orElse(null);
		log.debug("Found verified addresses for {} - {}", verifiedAddresses, identity);
		return verifiedAddresses;
	}

	@Override
	@CacheEvict(cacheNames = SOCIALS_CACHE_NAME)
	public void cleanCache(String identity) {
		log.debug("Evicting socials cache for {} key", identity);
	}

	@Override
	@Cacheable(cacheNames = SOCIALS_CACHE_NAME, unless = "#result==null")
	public Wallet getSocialMetadata(String identity) {
		try {
			ClientGraphQlResponse socialMetadataResponse = airstackGraphQlClient.documentName(
					"getSocialMetadata")
					.variable("identity", identity)
					.execute()
					.onErrorResume(exception -> {
						log.error("Error fetching {} - {}", identity, exception.getMessage());
						return Mono.empty();
					})
					.block();

			if (socialMetadataResponse != null) {
				if (log.isTraceEnabled()) {
					log.trace("Fetched socialMetadata for {}: {}", identity, socialMetadataResponse);
				} else {
					log.debug("Fetched socialMetadata for {}", identity);
				}
				return socialMetadataResponse.field("Wallet").toEntity(Wallet.class);
			}
		} catch (Throwable t) {
			if (log.isTraceEnabled()) {
				log.error("Full Error:", t);
			} else {
				log.error("Error: {}", t.getMessage());
			}
		}
		return null;
	}

	@Override
	@Cacheable(cacheNames = SOCIALS_INSIGHTS_CACHE_NAME, unless = "#result==null")
	public Wallet getSocialInsights(String identity, String me) {
		try {
			val socialMetadataResponse = airstackGraphQlClient.documentName(
					"getSocialInsights")
					.variable("identity", identity)
					.variable("me", me)
					.execute()
					.onErrorResume(exception -> {
						log.error("Error fetching {} - {}", identity, exception.getMessage());
						return Mono.empty();
					})
					.block();

			if (socialMetadataResponse != null) {
				if (log.isTraceEnabled()) {
					log.trace("Fetched socialMetadata for {}: {}", identity, socialMetadataResponse);
				} else {
					log.debug("Fetched socialMetadata for {}", identity);
				}

				val wallet = socialMetadataResponse.field("Wallet").toEntity(Wallet.class);

				if (wallet != null) {
					val followings = socialMetadataResponse.field("Wallet.socialFollowings.Following")
							.toEntityList(SocialFollowing.class);

					val followers = socialMetadataResponse.field("Wallet.socialFollowers.Follower")
							.toEntityList(SocialFollower.class);

					val socialFollowings = new SocialFollowingOutput.Builder().Following(followings).build();

					val socialFollowers = new SocialFollowerOutput.Builder().Follower(followers).build();

					wallet.setSocialFollowings(socialFollowings);
					wallet.setSocialFollowers(socialFollowers);
				}
				return wallet;
			}
		} catch (Throwable t) {
			if (log.isTraceEnabled()) {
				log.error("Full Error:", t);
			} else {
				log.error("Error: {}", t.getMessage());
			}
		}
		return null;
	}

	@Override
	public FarcasterChannel getFarcasterChannelByChannelId(String channelId) {
		try {
			val response = airstackGraphQlClient.documentName("getFarcasterChannelForChannelId")
					.variable("channelId", channelId)
					.execute()
					.block();

			if (response != null) {
				val channels = response.field("FarcasterChannels.FarcasterChannel")
						.toEntityList(FarcasterChannel.class);

				if (!channels.isEmpty()) {
					val channel = channels.get(0);
					log.debug("Fetched Farcaster channel for channelId {}: {}", channelId, channel);
					return channel;
				}
			}
		} catch (Throwable t) {
			log.error("Error during fetching Farcaster channel for channelId: {}, error: {} - {}",
					channelId,
					t.getMessage(),
					log.isTraceEnabled() ? t : null);
		}

		log.warn("Failed to fetch Farcaster channel for channelId: {}", channelId);
		return null;
	}
}
