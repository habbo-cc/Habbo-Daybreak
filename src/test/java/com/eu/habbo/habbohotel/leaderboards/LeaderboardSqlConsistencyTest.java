package com.eu.habbo.habbohotel.leaderboards;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeaderboardSqlConsistencyTest {
    @Test
    void standaloneGameInstallersKeepTheCanonicalLeaderboardSchema() throws IOException {
        String canonical = Files.readString(Path.of("sqlupdates", "leaderboards.sql"));
        for (String installer : new String[]{"battleball.sql", "snowstorm.sql"}) {
            String candidate = Files.readString(Path.of("sqlupdates", installer));
            assertEquals(table(canonical, "leaderboards"), table(candidate, "leaderboards"), installer);
            assertEquals(table(canonical, "leaderboard_stats"),
                    table(candidate, "leaderboard_stats"), installer);
        }
    }

    private static String table(String sql, String name) {
        Pattern pattern = Pattern.compile("CREATE TABLE IF NOT EXISTS `" + Pattern.quote(name)
                + "`.*?;", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(sql);
        assertTrue(matcher.find(), "Missing SQL table " + name);
        return matcher.group().replaceAll("\\s+", " ").trim();
    }
}
