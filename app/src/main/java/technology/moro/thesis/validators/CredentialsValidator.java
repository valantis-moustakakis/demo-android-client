package technology.moro.thesis.validators;

import static technology.moro.thesis.Constants.EMAIL_ADDRESS_REGEX;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CredentialsValidator {

    private static final int maxEmailLength = 50;
    private static final int minPasswordLength = 7;
    private static final int maxPasswordLength = 30;

    public static boolean validateEmail(String email) {
        Pattern pattern = Pattern.compile(EMAIL_ADDRESS_REGEX);
        Matcher matcher = pattern.matcher(email);
        return (email.length() <= maxEmailLength && matcher.matches());
    }

    public static boolean validatePassword(String password) {
        return password.length() <= maxPasswordLength && password.length() >= minPasswordLength;
    }

    public static boolean validateConfirmPassword(String password, String validationPassword) {
        return (validationPassword != null && validationPassword.equals(password));
    }
}
