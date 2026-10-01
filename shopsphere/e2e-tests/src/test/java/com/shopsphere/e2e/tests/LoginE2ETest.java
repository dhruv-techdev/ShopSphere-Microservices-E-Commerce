package com.shopsphere.e2e.tests;

import com.shopsphere.e2e.api.Credentials;
import com.shopsphere.e2e.pages.AppShell;
import com.shopsphere.e2e.pages.LoginPage;
import com.shopsphere.e2e.pages.OrderListPage;
import com.shopsphere.e2e.support.Ui;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class LoginE2ETest extends BaseE2ETest {

    @Test(description = "An admin signs in, lands on the dashboard and signs out; the session is gone afterwards")
    public void adminSignsInAndOut() {
        Credentials admin = DATA.admin();

        AppShell shell = LoginPage.open(driver).signIn(admin);

        assertEquals(Ui.path(driver), "/dashboard");
        assertTrue(shell.userMenuText().contains(admin.email()), "toolbar shows the signed-in admin");

        shell.signOut();
        driver.get(CONFIG.url("/dashboard"));
        LoginPage.at(driver);
    }

    @Test(description = "A wrong password is rejected and the password field is cleared")
    public void wrongPasswordIsRejected() {
        LoginPage login = LoginPage.open(driver)
                .signInExpectingError(DATA.admin().withPassword("not-the-password"));

        assertEquals(login.errorMessage(), "Invalid email or password.");
        assertEquals(login.passwordValue(), "");
        assertEquals(Ui.path(driver), "/login");
    }

    @Test(description = "A valid CUSTOMER account cannot use the admin console")
    public void customerIsRejected() {
        Credentials customer = DATA.newCustomer().credentials();

        LoginPage login = LoginPage.open(driver).signInExpectingError(customer);

        assertEquals(login.errorMessage(), "This account does not have administrator access.");
        assertEquals(Ui.path(driver), "/login");
    }

    @Test(description = "A deep link survives the sign-in redirect")
    public void deepLinkIsRestoredAfterSignIn() {
        driver.get(CONFIG.url("/orders"));
        LoginPage login = LoginPage.at(driver);

        assertEquals(login.returnUrl(), Optional.of("/orders"));

        login.signIn(DATA.admin());
        OrderListPage.at(driver);
    }
}
