package com.zone5cloud.http.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.Before;
import org.junit.Test;

import com.zone5cloud.core.Z5Error;
import com.zone5cloud.core.enums.UnitMeasurement;
import com.zone5cloud.core.oauth.OAuthToken;
import com.zone5cloud.core.users.LoginResponse;
import com.zone5cloud.core.users.RegisterUser;
import com.zone5cloud.core.users.User;
import com.zone5cloud.core.users.UserPreferences;
import com.zone5cloud.http.core.api.UserAPI;
import com.zone5cloud.http.core.responses.Z5HttpResponse;
import com.zone5cloud.http.core.responses.Z5HttpResponseHandler;

public class TestUsersAPI extends BaseTest {

	UserAPI api = new UserAPI();
	
	// This is your allocated clientId and secret - these can be set to null for S-Digital environments
	String clientId = "1er3227s1mia3pkqrngntl4sv6"; 	// "<your OAuth clientId issued by Zone5>";
	String clientSecret = "19re5046mf15n5m38klrmnr9sjtcia4sdv4hpn0ivoshm1tu72cp"; // "<your OAuth secret issued by Zone5>";
	
	@Before
	public void setup() throws InterruptedException, ExecutionException {
		login();
	}
	
	/** To run this test you need a valid clientId & secret */
	@Test
	public void testRegistrationLoginDelete() throws Exception {
		String[] parts = TEST_EMAIL.split("@");
		String email = String.format("%s+%d@%s", parts[0], System.currentTimeMillis(), parts[1]);
		String password = "superS3cretStu55";
		String firstname = "Test";
		String lastname = "User";
		
		RegisterUser register = new RegisterUser();
		register.setEmail(email);
		register.setPassword(password);
		register.setFirstname(firstname);
		register.setLastname(lastname);
		
		// For S-Digital registrations (optional)
		//register.setParams(new HashMap<String, String>(2));
		//register.getParams().put("regoSource", "Rider Hub");
		//register.getParams().put("regoKey", "<alternate GIGYA ACCESS KEY>");
		
		// optional - set weight, thresholds, dob, gender etc
		register.setWeight(80.1d);
		
		// check that this user does not yet exist in the system
		assertFalse(api.isEmailRegistered(email).get().getResult());
		
		User user = api.register(register).get().getResult();
		assertNotNull(user.getId()); // our unique userId
		assertEquals(email, user.getEmail());
		assertEquals(Locale.getDefault().toString(), user.getLocale());
		
		// Note - in S-Digital, the user will need to validate their email before they can login...
		if (api.getClient().isSpecialized() && clientId != SBC_NO_VERIFICATION_GIGYA) {
			System.out.println("Waiting for confirmation that you have verified your email address ... press Enter when done");
			System.in.read();
		}
		
		// Login and set our bearer token
		Future<Z5HttpResponse<LoginResponse>> f = api.login(email, password, clientId, clientSecret);
		LoginResponse r = f.get().getResult();
		assertNotNull(r.getToken());
		
		// Try it out!
		User me = api.me().get().getResult();
		assertEquals(me.getId(), user.getId());
		
		// check that this user is now considered registered
		assertTrue(api.isEmailRegistered(email).get().getResult());
		assertTrue(api.logout().get().getResult());
		assertNull(Z5HttpClient.get().getToken());
		
		assertTrue(api.isEmailRegistered(email).get().getResult());
		
		// Oops I forgot my password - send me an email with a magic link
		assertTrue(api.resetPassword(email).get().getResult());
		
		// Log back in
		f = api.login(email, password, clientId, clientSecret);
		r = f.get().getResult();
		assertNotNull(r.getToken());
	
		me = api.me().get().getResult();
		assertEquals(me.getId(), user.getId());
		
		// Change my password and try it out
		assertEquals(200, api.changePassword(password, "myNewPassword123!!").get().getStatusCode());
		assertTrue(api.logout().get().getResult());
		
		f = api.login(email, "myNewPassword123!!", clientId, clientSecret);
		r = f.get().getResult();
		assertNotNull(r.getToken());
		
		// Exercise the refresh access token
		if (api.getClient().isSpecialized() && api.getClient().getToken().getRefreshToken() == null) {
			OAuthToken alt = api.refreshToken().get().getResult();
			assertNotNull(alt.getToken());
			assertNotNull(alt.getTokenExp());
			me = api.me().get().getResult();
			assertEquals(me.getId(), user.getId());
		}

		
		// S-Digital Needs to be deleted via GIGYA
		if (!api.getClient().isSpecialized()) {
			// Delete this account
			assertEquals(204, api.deleteAccount(me.getId()).get().getStatusCode());
			
			// We are no longer valid!
			assertEquals(401, api.me().get().getStatusCode());
			
			assertEquals(401, api.login(email, password, clientId, clientSecret).get().getStatusCode());
		}
	}
	
	@Test
	public void testMe() throws Exception {
		
		Future<Z5HttpResponse<User>> f = api.me();
		Z5HttpResponse<User> r = f.get();
		assertEquals(200, r.getStatusCode());
		assertNotNull(r.getResult().getId());
		assertNotNull(r.getResult().getFirstname());
		assertNotNull(r.getResult().getLastname());
		assertNotNull(r.getResult().getEmail());
	}
	
	@Test
	public void testUserPreferences() throws Exception {
		
		Future<Z5HttpResponse<UserPreferences>> f = api.getUserPreferences(api.me().get().getResult().getId());
		Z5HttpResponse<UserPreferences> r = f.get();
		assertEquals(200, r.getStatusCode());
		assertNotNull(r.getResult().getMetric());
		
		UserPreferences p = new UserPreferences();
		p.setMetric(UnitMeasurement.imperial);
		assertEquals(true, api.setUserPreferences(p).get().getResult());
		assertEquals(UnitMeasurement.imperial, api.getUserPreferences(api.me().get().getResult().getId()).get().getResult().getMetric());
		
		p = new UserPreferences();
		p.setMetric(UnitMeasurement.metric);
		assertEquals(true, api.setUserPreferences(p).get().getResult());
		assertEquals(UnitMeasurement.metric, api.getUserPreferences(api.me().get().getResult().getId()).get().getResult().getMetric());
	}
	
	@Test
	public void testMeAsync() throws Exception {
		CountDownLatch l = new CountDownLatch(1);
		
		Future<Z5HttpResponse<User>> f = api.me(new Z5HttpResponseHandler<User>() {
			
			@Override
			public void onSuccess(int code, User result) {
				assertEquals(200, code);
				assertNotNull(result.getId());
				assertNotNull(result.getFirstname());
				assertNotNull(result.getLastname());
				assertNotNull(result.getEmail());
				l.countDown();
			}
			
			@Override
			public void onError(Throwable t, Z5Error error) {
				l.countDown();
				assertTrue(false);
				
			}
			
			@Override
			public void onError(int code, Z5Error error) {
				l.countDown();	
				assertTrue(false);
			}
		});
		
		f.get();
		l.await();
		
	}

}
