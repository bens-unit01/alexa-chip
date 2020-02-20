
/**
 * @module
 * This module defines the settings that need to be configured for a new
 * environment.
 * The clientId and clientSecret are provided when you create
 * a new security profile in Login with Amazon.  
 * 
 * You will also need to specify
 * the redirect url under allowed settings as the return url that LWA
 * will call back to with the authorization code.  The authresponse endpoint
 * is setup in app.js, and should not be changed.  
 * 
 * lwaRedirectHost and lwaApiHost are setup for login with Amazon, and you should
 * not need to modify those elements.
 */
var config = {
    clientId: "amzn1.application-oa2-client.71a603166b4b4c30aa35e6809a59a232",
    clientSecret: "469170eaaa983f2518d2443d424e87fff54ee0bb9cef27362cf0a032cb10bd1f",
    redirectUrl: 'https://localhost:3000/authresponse',
    lwaRedirectHost: "amazon.com",
    lwaApiHost: "api.amazon.com",
    validateCertChain: true,
    sslKey: "C:\\Users\\raouf\\Documents\\ws-java\\alexa-testing\\alexa-test-version-desktop\\certs\\server\\node.key",
    sslCert: "C:\\Users\\raouf\\Documents\\ws-java\\alexa-testing\\alexa-test-version-desktop\\certs\\server\\node.crt",
    sslCaCert: "C:\\Users\\raouf\\Documents\\ws-java\\alexa-testing\\alexa-test-version-desktop\\certs\\ca\\ca.crt", 
    products: {
        "wow_robot_01": ["123456"],
    },
};

module.exports = config
