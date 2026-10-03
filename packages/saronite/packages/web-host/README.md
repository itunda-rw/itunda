# Saronite Web Host

The Web Host is the browser-side runtime boundary for Saronite mini-apps.

It uses the same versioned protocol as the Android and iOS hosts. Production code should provide an explicit targetOrigin when using the window transport and should grant only the permissions required by a mini-app.

The package contains no DevTools or mock-host dependency. DevTools can connect through the same transport contract without becoming part of a production mini-app bundle.
