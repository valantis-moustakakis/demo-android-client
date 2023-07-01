package technology.moro.thesis.validators;

import static technology.moro.thesis.Constants.EMAIL_ADDRESS_REGEX;
import static technology.moro.thesis.Constants.PASSWORD_REGEX;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CredentialsValidator {

    public static boolean validateEmail(String email) {
        Pattern pattern = Pattern.compile(EMAIL_ADDRESS_REGEX);
        Matcher matcher = pattern.matcher(email);
        return matcher.matches();
    }

    public static boolean validatePassword(String password) {
        Pattern pattern = Pattern.compile(PASSWORD_REGEX);
        Matcher matcher = pattern.matcher(password);
        return matcher.matches();
    }

    public static boolean validateConfirmPassword(String password, String validationPassword) {
        return (validationPassword != null && validationPassword.equals(password));
    }
}
