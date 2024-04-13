package ua.sinaver.web3.payflow.message;

public record CastActionMeta(String name, String icon, String description, String aboutUrl,
                             Action action) {
	public record Action(String type) {
	}
}
