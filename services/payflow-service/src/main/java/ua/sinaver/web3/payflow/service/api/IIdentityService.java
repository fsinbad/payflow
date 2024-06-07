package ua.sinaver.web3.payflow.service.api;

import ua.sinaver.web3.payflow.data.User;
import ua.sinaver.web3.payflow.message.IdentityMessage;

import java.util.List;

public interface IIdentityService {
	User getFidProfile(int fid, String identity);

	User getFidProfile(String fname, String identity);

	List<User> getFidProfiles(int fid);

	List<User> getFidProfiles(String fname);

	List<String> getFidAddresses(int fid);

	String getENSAddress(String ens);

	List<String> getFnameAddresses(String fname);

	List<User> getFidProfiles(List<String> addresses);

	String getFidFname(int fid);

	String getIdentityFname(String identity);

	String getIdentityFid(String identity);

	List<IdentityMessage> getIdentitiesInfo(int fid);

	List<IdentityMessage> getIdentitiesInfo(List<String> identities);

	List<IdentityMessage> getIdentitiesInfo(List<String> identities, String me);
}
