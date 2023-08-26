module security.service {
    requires miglayout.swing;
    requires java.desktop;
    requires image.service;
    requires com.google.common;
    requires com.google.gson;
    requires java.prefs;
    opens com.udacity.catpoint.security.data to com.google.gson;
}