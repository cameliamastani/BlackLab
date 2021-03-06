package nl.inl.blacklab.server.auth;

import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.server.BlackLabServer;
import nl.inl.blacklab.server.jobs.User;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Authentication system used for debugging.
 *
 * Requests from debug IPs (specified in config file) are automatically
 * logged in as the specified userId.
 */
public class AuthDebugFixed {

    static final Logger logger = LogManager.getLogger(AuthDebugFixed.class);
    
	private String userId;

	public AuthDebugFixed(Map<String, Object> parameters) {
        boolean hasUserId = parameters.containsKey("userId");
        int expectedParameters = hasUserId ? 1 : 0;
        if (parameters.size() > expectedParameters)
            logger.warn("AuthDebugFixed only takes one parameter (userId), but other parameters were passed.");
		Object u = parameters.get("userId");
        this.userId = u != null ? u.toString() : "DEBUG-USER";
	}

	public User determineCurrentUser(HttpServlet servlet,
			HttpServletRequest request) {

		String sessionId = request.getSession().getId();

		// Is client on debug IP?
		SearchManager searchMan = ((BlackLabServer)servlet).getSearchManager();
		String ip = request.getRemoteAddr();
		if (!searchMan.config().isDebugMode(ip) && !searchMan.config().overrideUserId(ip)) {
			return User.anonymous(sessionId);
		}

		// Return the appropriate User object
		return User.loggedIn(userId, sessionId);
	}

}
