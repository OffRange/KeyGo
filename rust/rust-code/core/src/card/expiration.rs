//! `MM/YY` expiration-date formatting for text being typed or deleted live.

use super::digits_only;

const SEPARATOR: char = '/';

/// Applies expiration-date formatting to an edit, given the `previous` text and the `proposed`
/// text the user just produced. The separator is auto-inserted while typing forward; deletions
/// erase it together with adjacent digits so the user can never get stuck on `MM/`:
///
/// - `12/34` -> `12/3` -> `12` -> `1` -> `` (each backspace removes one visible character)
/// - deleting the separator itself also drops the preceding month digit (`12/` -> `1`, `05/` -> `0`)
pub fn format_expiration_after_edit(previous: &str, proposed: &str) -> String {
    let deleting = proposed.chars().count() < previous.chars().count();
    // Backspacing the separator leaves the bare month behind; take the month digit with it.
    let deleted_separator =
        deleting && previous.ends_with(SEPARATOR) && !proposed.ends_with(SEPARATOR);
    let source = if deleted_separator {
        let mut trimmed = proposed.to_string();
        trimmed.pop();
        trimmed
    } else {
        proposed.to_string()
    };
    format_expiration_inner(&source, !deleting)
}

/// Reformats arbitrary `input` into a partial-or-complete `MM/YY` expiration date.
///
/// - A leading digit greater than `1` is padded into a complete single-digit month (`5` -> `05`).
/// - A second month digit that would form an invalid month is dropped (`13` -> `1`, `00` -> `0`).
/// - The separator follows a complete month; the year is capped at two digits.
///
/// `append_trailing_separator` controls whether a complete month with no year yet renders as
/// `MM/` (typing forward) or plain `MM` (deleting, so the user can erase back into the month).
fn format_expiration_inner(input: &str, append_trailing_separator: bool) -> String {
    let digits = digits_only(input);
    let Some(first) = digits.chars().next() else {
        return String::new();
    };

    let (month, year_digits): (String, &str) = if first > '1' {
        // 2..=9 can only be a single-digit month, so pad it and treat it as complete.
        (format!("0{first}"), &digits[1..])
    } else {
        let second = digits[1..].chars().next();
        let month_complete = match second {
            None => false,
            Some(s) if first == '0' => ('1'..='9').contains(&s), // 01..=09; 00 is invalid
            Some(s) => ('0'..='2').contains(&s),                 // 10, 11, 12
        };
        if !month_complete {
            return first.to_string();
        }
        (digits[..2].to_string(), &digits[2..])
    };

    let year: String = year_digits.chars().take(2).collect();
    if !year.is_empty() {
        format!("{month}{SEPARATOR}{year}")
    } else if append_trailing_separator {
        format!("{month}{SEPARATOR}")
    } else {
        month
    }
}

#[cfg(test)]
mod tests {
    use super::{format_expiration_after_edit, format_expiration_inner};

    #[test]
    fn expiration_keeps_a_month_still_being_typed() {
        assert_eq!(format_expiration_inner("", true), "");
        assert_eq!(format_expiration_inner("0", true), "0");
        assert_eq!(format_expiration_inner("1", true), "1");
    }

    #[test]
    fn expiration_pads_a_leading_digit_greater_than_one() {
        assert_eq!(format_expiration_inner("2", true), "02/");
        assert_eq!(format_expiration_inner("5", true), "05/");
        assert_eq!(format_expiration_inner("9", true), "09/");
    }

    #[test]
    fn expiration_completes_two_digit_months() {
        assert_eq!(format_expiration_inner("10", true), "10/");
        assert_eq!(format_expiration_inner("12", true), "12/");
        assert_eq!(format_expiration_inner("09", true), "09/");
    }

    #[test]
    fn expiration_rejects_an_impossible_second_digit() {
        assert_eq!(format_expiration_inner("13", true), "1");
        assert_eq!(format_expiration_inner("19", true), "1");
        assert_eq!(format_expiration_inner("00", true), "0");
    }

    #[test]
    fn expiration_appends_the_year_after_the_separator() {
        assert_eq!(format_expiration_inner("123", true), "12/3");
        assert_eq!(format_expiration_inner("1234", true), "12/34");
        assert_eq!(format_expiration_inner("099", true), "09/9");
        assert_eq!(format_expiration_inner("527", true), "05/27");
    }

    #[test]
    fn expiration_caps_the_year_and_strips_non_digits() {
        assert_eq!(format_expiration_inner("123456", true), "12/34");
        assert_eq!(format_expiration_inner("1a2", true), "12/");
        assert_eq!(format_expiration_inner("12/34", true), "12/34");
        assert_eq!(format_expiration_inner(" 5 ", true), "05/");
    }

    #[test]
    fn expiration_edit_types_forward_and_auto_inserts_the_separator() {
        assert_eq!(format_expiration_after_edit("1", "12"), "12/");
        assert_eq!(format_expiration_after_edit("", "5"), "05/");
        assert_eq!(format_expiration_after_edit("12/", "12/3"), "12/3");
    }

    #[test]
    fn expiration_edit_deletes_year_digits_then_the_separator_with_the_month() {
        // 12/34 -> 12/3 -> 12 -> 1 -> "" (one visible char per backspace)
        assert_eq!(format_expiration_after_edit("12/34", "12/3"), "12/3");
        assert_eq!(format_expiration_after_edit("12/3", "12/"), "12");
        assert_eq!(format_expiration_after_edit("12/", "12"), "1");
        assert_eq!(format_expiration_after_edit("12", "1"), "1");
        assert_eq!(format_expiration_after_edit("1", ""), "");
    }

    #[test]
    fn expiration_edit_collapses_a_padded_month_on_separator_deletion() {
        // 05/ -> delete -> 0
        assert_eq!(format_expiration_after_edit("05/", "05"), "0");
    }
}
