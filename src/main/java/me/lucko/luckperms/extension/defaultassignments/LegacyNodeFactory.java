/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.extension.defaultassignments;

import com.google.common.base.Splitter;
import me.lucko.luckperms.common.node.factory.NodeBuilders;
import net.luckperms.api.context.DefaultContextKeys;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeBuilder;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class LegacyNodeFactory {
    private LegacyNodeFactory() {}

    private static final String[] CONTEXT_DELIMITERS = new String[]{"=", "(", ")", ","};
    private static final Pattern NODE_CONTEXTS_PATTERN = Pattern.compile("\\(.+\\).*");
    private static final Pattern LEGACY_SERVER_DELIM = compileDelimiterPattern("/", "\\");
    private static final Splitter LEGACY_SERVER_SPLITTER = Splitter.on(LEGACY_SERVER_DELIM).limit(2);
    private static final Pattern LEGACY_WORLD_DELIM = compileDelimiterPattern("-", "\\");
    private static final Splitter LEGACY_WORLD_SPLITTER = Splitter.on(LEGACY_WORLD_DELIM).limit(2);
    private static final Pattern LEGACY_EXPIRY_DELIM = compileDelimiterPattern("$", "\\");
    private static final Splitter LEGACY_EXPIRY_SPLITTER = Splitter.on(LEGACY_EXPIRY_DELIM).limit(2);
    private static final Pattern LEGACY_CONTEXT_DELIM = compileDelimiterPattern(")", "\\");
    private static final Splitter CONTEXT_SPLITTER = Splitter.on(LEGACY_CONTEXT_DELIM).limit(2);
    private static final Pattern LEGACY_CONTEXT_PAIR_DELIM = compileDelimiterPattern(",", "\\");
    private static final Pattern LEGACY_CONTEXT_PAIR_PART_DELIM = compileDelimiterPattern("=", "\\");
    private static final Splitter.MapSplitter LEGACY_CONTEXT_PART_SPLITTER = Splitter.on(LEGACY_CONTEXT_PAIR_DELIM)
            .withKeyValueSeparator(Splitter.on(LEGACY_CONTEXT_PAIR_PART_DELIM));

    public static Node fromLegacyString(String s) {
        return unpackLegacyFormat(s, true).build();
    }

    private static NodeBuilder<?, ?> unpackLegacyFormat(String permission, boolean value) {
        // if contains /
        if (LEGACY_SERVER_DELIM.matcher(permission).find()) {
            // 0=server(+world)   1=node
            Iterator<String> parts = LEGACY_SERVER_SPLITTER.split(permission).iterator();
            String parts0 = parts.next();
            String parts1 = parts.next();

            // WORLD SPECIFIC
            // if parts[0] contains -
            if (LEGACY_WORLD_DELIM.matcher(parts0).find()) {
                // 0=server   1=world
                Iterator<String> serverParts = LEGACY_WORLD_SPLITTER.split(parts0).iterator();
                String serverParts0 = serverParts.next();
                String serverParts1 = serverParts.next();

                // if parts[1] contains $
                if (LEGACY_EXPIRY_DELIM.matcher(parts1).find()) {
                    // 0=node   1=expiry
                    Iterator<String> tempParts = LEGACY_EXPIRY_SPLITTER.split(parts1).iterator();
                    String tempParts0 = tempParts.next();
                    String tempParts1 = tempParts.next();

                    return unpackContexts(tempParts0).withContext(DefaultContextKeys.SERVER_KEY, serverParts0).withContext(DefaultContextKeys.WORLD_KEY, serverParts1).expiry(Long.parseLong(tempParts1)).value(value);
                } else {
                    return unpackContexts(parts1).withContext(DefaultContextKeys.SERVER_KEY, serverParts0).withContext(DefaultContextKeys.WORLD_KEY, serverParts1).value(value);
                }
            } else {
                // SERVER BUT NOT WORLD SPECIFIC

                // if parts[1] contains $
                if (LEGACY_EXPIRY_DELIM.matcher(parts1).find()) {
                    // 0=node   1=expiry
                    Iterator<String> tempParts = LEGACY_EXPIRY_SPLITTER.split(parts1).iterator();
                    String tempParts0 = tempParts.next();
                    String tempParts1 = tempParts.next();

                    return unpackContexts(tempParts0).withContext(DefaultContextKeys.SERVER_KEY, parts0).expiry(Long.parseLong(tempParts1)).value(value);
                } else {
                    return unpackContexts(parts1).withContext(DefaultContextKeys.SERVER_KEY, parts0).value(value);
                }
            }
        } else {
            // NOT SERVER SPECIFIC

            // if s contains $
            if (LEGACY_EXPIRY_DELIM.matcher(permission).find()) {
                // 0=node   1=expiry
                Iterator<String> tempParts = LEGACY_EXPIRY_SPLITTER.split(permission).iterator();
                String tempParts0 = tempParts.next();
                String tempParts1 = tempParts.next();

                return unpackContexts(tempParts0).expiry(Long.parseLong(tempParts1)).value(value);
            } else {
                return unpackContexts(permission).value(value);
            }
        }
    }

    private static NodeBuilder<?, ?> unpackContexts(String permission) {
        if (!NODE_CONTEXTS_PATTERN.matcher(permission).matches()) {
            return NodeBuilders.determineMostApplicable(permission);
        } else {
            List<String> contextParts = CONTEXT_SPLITTER.splitToList(permission.substring(1));
            // 0 = context, 1 = node

            NodeBuilder<?, ?> builder = NodeBuilders.determineMostApplicable(contextParts.get(1));
            try {
                Map<String, String> map = LEGACY_CONTEXT_PART_SPLITTER.split(contextParts.get(0));
                for (Map.Entry<String, String> e : map.entrySet()) {
                    builder.withContext(
                            unescapeDelimiters(e.getKey(), CONTEXT_DELIMITERS),
                            unescapeDelimiters(e.getValue(), CONTEXT_DELIMITERS)
                    );
                }
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
            return builder;
        }
    }

    private static Pattern compileDelimiterPattern(String delimiter, String escape) throws PatternSyntaxException {
        String pattern = "(?<!" + Pattern.quote(escape) + ")" + Pattern.quote(delimiter);
        return Pattern.compile(pattern);
    }

    private static String unescapeDelimiters(String s, String... delimiters) {
        if (s == null) {
            return null;
        }

        for (String d : delimiters) {
            s = s.replace("\\" + d, d);
        }
        return s;
    }

}