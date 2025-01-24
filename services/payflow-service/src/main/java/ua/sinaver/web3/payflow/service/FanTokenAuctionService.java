package ua.sinaver.web3.payflow.service;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ua.sinaver.web3.payflow.entity.User;
import ua.sinaver.web3.payflow.message.ContactWithFanTokenAuction;
import ua.sinaver.web3.payflow.message.SocialInfo;
import ua.sinaver.web3.payflow.service.api.IContactBookService;
import ua.sinaver.web3.payflow.service.api.ISocialGraphService;

import java.util.*;
import java.util.stream.Collectors;

import static ua.sinaver.web3.payflow.config.CacheConfig.FAN_TOKENS_CACHE_NAME;

@Slf4j
@Service
@Transactional
public class FanTokenAuctionService {

	private static final String FARCASTER_DAPP = "farcaster";

	@Autowired
	private IContactBookService contactBookService;

	@Autowired
	private ISocialGraphService socialGraphService;

	@Cacheable(value = FAN_TOKENS_CACHE_NAME, key = "#user.identity", unless = "#result.isEmpty()")
	public List<ContactWithFanTokenAuction> getFanTokenAuctionsAmongContacts(User user) {
		val contacts = contactBookService.getAllContacts(user).contacts();
		if (!contacts.isEmpty()) {
			// Create a map of profile names to contacts, filtering out null profile names
			val usernameToContactMap = contacts.stream()
					.map(contact -> new AbstractMap.SimpleEntry<>(
							contact.data().meta().socials().stream()
									.filter(social -> FARCASTER_DAPP.equals(social.dappName()))
									.map(SocialInfo::profileName)
									.findFirst()
									.orElse(null),
							contact)
					)
					.filter(entry -> entry.getKey() != null)
					.collect(Collectors.toMap(
							Map.Entry::getKey,
							Map.Entry::getValue,
							(existing, replacement) -> existing
					));

			val usernames = usernameToContactMap.keySet().stream().toList();
			val auctions = socialGraphService.getFanTokenAuctions(usernames);

			val contactWithAuctionList = auctions.stream()
					.map(a -> {
								val contact = usernameToContactMap.get(a.getEntityName());
								if (contact != null && contact.data() != null) {
									val supply = (int) (a.getAuctionSupply() / Math.pow(10, a.getDecimals()));
									return new ContactWithFanTokenAuction(contact,
											new ContactWithFanTokenAuction.FanTokenAuction(
													a.getEntityName(),
													supply,
													a.getEstimatedStartTimestamp(),
													a.getLaunchCastUrl()));
								}
								return null;
							}
					)
					.filter(Objects::nonNull)
					.collect(Collectors.toList());

			log.debug("Contacts with auctions for {}: {}", contactWithAuctionList, user.getIdentity());
			return contactWithAuctionList;
		}
		return Collections.emptyList();
	}
}
