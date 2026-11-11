import datetime
import unittest
import unittest.mock

import nightly_version as versions


class IsoWeekVersionTest(unittest.TestCase):
    def test_year_and_week_come_from_the_iso_calendar(self):
        # 2026-11-13 is ISO week 46.
        moment = datetime.datetime(2026, 11, 13, tzinfo=datetime.timezone.utc)
        self.assertEqual(versions.iso_week_version(moment), (26, 46))

    def test_iso_week_year_differs_from_calendar_year_at_year_end(self):
        # 2026-12-31 belongs to ISO week 53 of ISO year 2026; 2027-01-01 is 2026-W53.
        moment = datetime.datetime(2027, 1, 1, tzinfo=datetime.timezone.utc)
        self.assertEqual(versions.iso_week_version(moment), (26, 53))

    def test_first_second_and_third_builds_of_the_week(self):
        self.assertEqual(versions.format_version(26, 46, 0), "26w46a")
        self.assertEqual(versions.format_version(26, 46, 1), "26w46b")
        self.assertEqual(versions.format_version(26, 46, 2), "26w46c")

    def test_sequence_wraps_after_twenty_six_builds(self):
        self.assertEqual(versions.format_version(26, 46, 25), "26w46z")
        self.assertEqual(versions.format_version(26, 46, 26), "26w46a")

    def test_single_digit_week_is_zero_padded(self):
        self.assertEqual(versions.format_version(26, 3, 0), "26w03a")

    def test_version_code_increases_with_each_build(self):
        codes = [versions.version_code(194, 26, 46, i) for i in range(3)]
        self.assertEqual(codes, sorted(set(codes)))

    def test_version_code_increases_across_weeks_and_years(self):
        self.assertLess(versions.version_code(194, 26, 46, 25), versions.version_code(194, 26, 47, 0))
        self.assertLess(versions.version_code(194, 26, 53, 25), versions.version_code(194, 27, 1, 0))

    def test_nightly_installs_over_the_release_it_is_based_on(self):
        for base in (1, 194, 9999):
            self.assertGreater(versions.version_code(base, 26, 46, 0), base)

    def test_next_release_installs_over_every_nightly(self):
        # A release bump must clear the whole reserved nightly range of the previous one.
        base = 194
        highest_nightly = versions.version_code(base, 99, 53, 25)
        self.assertLess(highest_nightly, (base + 1) * versions.NIGHTLY_CODE_RANGE)

    def test_version_code_fits_in_a_signed_32_bit_int(self):
        self.assertLess(versions.version_code(9999, 99, 53, 25), 2**31)

    def test_offset_beyond_the_reserved_range_is_rejected(self):
        with unittest.mock.patch.object(versions, "NIGHTLY_CODE_RANGE", 10):
            with self.assertRaises(ValueError):
                versions.version_code(194, 26, 46, 0)

    def test_reads_release_version_code_from_the_env_override_form(self):
        text = '        versionCode = (System.getenv("BUILD_VERSION_CODE") ?: "194").toInteger()'
        self.assertEqual(versions.release_version_code(text), 194)

    def test_reads_release_version_code_from_a_plain_literal(self):
        self.assertEqual(versions.release_version_code("        versionCode = 207"), 207)

    def test_counts_only_versions_of_the_requested_week(self):
        log = "\n".join(
            [
                "chore(nightly): 26w46a merge develop into nightly",
                "chore(nightly): 26w46b merge develop into nightly",
                "chore(nightly): 26w45z merge develop into nightly",
                "fix: something unrelated",
                "chore(nightly): 26w46 merge develop into nightly",
            ]
        )
        with unittest.mock.patch.object(versions, "run", return_value=log):
            self.assertEqual(versions.builds_this_week("nightly", 26, 46), 2)

    def test_missing_branch_history_starts_at_zero(self):
        with unittest.mock.patch.object(versions, "run", side_effect=OSError("no git")):
            self.assertEqual(versions.builds_this_week("nightly", 26, 46), 0)


if __name__ == "__main__":
    unittest.main()
