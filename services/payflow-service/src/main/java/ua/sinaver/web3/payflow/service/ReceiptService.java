package ua.sinaver.web3.payflow.service;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ua.sinaver.web3.payflow.entity.Payment;

@Slf4j
@Service
public class ReceiptService {

	@Autowired
	private LinkService linkService;

	public String getReceiptUrl(Payment payment) {
		return getReceiptUrl(payment, false, false);
	}

	public String getReceiptUrl(Payment payment, boolean fulfillment, boolean refund) {
		val receiptChainId = (fulfillment || refund) ? payment.getFulfillmentChainId() : payment.getNetwork();
		val receiptHash = fulfillment ? payment.getFulfillmentHash()
				: (refund ? payment.getRefundHash() : payment.getHash());
		val baseUrl = switch (receiptChainId) {
			case 8453 -> // Base Chain ID
					"https://basescan.org";
			case 10 -> // Optimism Chain ID
					"https://optimistic.etherscan.io";
			case 7777777 -> // Zora Chain ID
					"https://explorer.zora.energy";
			case 666666666 -> // Degen Chain ID
					"https://explorer.degen.tips";
			case 42161 -> // Arbitrum Chain ID
					"https://arbiscan.io";
			case 34443 -> // Mode Chain ID
					"https://modescan.io";
			case 480 -> // World chain Chain ID
					"https://worldscan.org";
			case 5112 -> // Ham Chain ID
					"https://explorer.ham.fun";
			default ->
					throw new IllegalArgumentException("Chain " + payment.getNetwork() + " not " +
							"supported!");
		};

		return baseUrl + "/tx/" + receiptHash;
	}
}
