package rikser123.crawler.utils;

import lombok.experimental.UtilityClass;

import java.util.Set;

@UtilityClass
public class CaptchaUtils {
  private static final int CAPTCHA_POSSIBLE_LENGTH_CONTENT = 1500;
  private static final Set<String> CAPTCHA_MARKERS = Set.of(
    "cf-browser-verification",
    "challenge-platform",
    "cf-turnstile",
    "cf-chl-bypass",
    "_cf_chl",
    "cloudflare",
    "just a moment...",
    "checking your browser",
    "please wait while we check your browser",

    "ddos-guard",
    "ddos-guard.net",
    "проверка браузера",
    "пожалуйста, подождите",
    "checking your browser before accessing",
    "_ddg_",

    "g-recaptcha",
    "recaptcha/api.js",
    "recaptcha-anchor",
    "data-sitekey",
    "no captcha reCAPTCHA",

    "hcaptcha.com",
    "h-captcha",
    "data-hcaptcha-site-key",

    "akamai",
    "_abck",
    "ak_bmsc",
    "akamai/amp",

    "_pxhd",
    "_pxvid",
    "px-cdn",
    "perimeterx",

    "verify you are human",
    "please complete the security check",
    "подтвердите, что вы не робот",
    "доступ ограничен",
    "сработала система защиты",
    "bot protection",
    "are you a robot"
  );

  public boolean isCaptcha(String html) {
    return html.length() < CAPTCHA_POSSIBLE_LENGTH_CONTENT &&
      CAPTCHA_MARKERS.stream().anyMatch(captcha -> html.toLowerCase().contains(captcha));
  }
}
