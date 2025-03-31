module com.vti.tuyn.actools {
    requires javafx.controls;
    requires javafx.fxml;

    requires com.dlsc.formsfx;
    requires org.seleniumhq.selenium.api;
    requires org.seleniumhq.selenium.chrome_driver;
    requires org.seleniumhq.selenium.support;
    requires io.github.bonigarcia.webdrivermanager;

    opens com.vti.tuyn.actools to javafx.fxml;
    exports com.vti.tuyn.actools;
}