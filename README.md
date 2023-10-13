![Smart Completion Mod](https://github.com/endorh/smart-completion/blob/1.20+/images/%5Bg%5Dolden_%5Bap%5Dple.png?raw=true)

![Minecraft: 1.16 - 1.20.1](https://img.shields.io/static/v1?label=&message=1.16%20-%201.20.1&color=2d2d2d&labelColor=4e4e4e&style=flat-square&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAAZdEVYdFNvZnR3YXJlAHBhaW50Lm5ldCA0LjAuMjCGJ1kDAAACoElEQVQ4T22SeU8aURTF/ULGtNRWWVQY9lXABWldIDPIMgVbNgEVtaa0damiqGBdipXaJcY2ofEf4ycbTt97pVAabzK5b27u+Z377kwXgK77QthRy7OfXbeJM+ttqKSXN8sdwbT/A0L7elmsYqrPHZmROLPh5YkV4oEBwaKuHj+yyJptLDoAhbq3O1V1XCVObY3FL24mfn5oRPrcwSCRfQOyNWcjVjZdCbtcdwcgXrXUspdOKbDN/XE9tiBJMhXHT60gUIT2dMhcDLMc3NVKQklz0QIkf5qlyEcO6Qs7yPhMJB4amDMFimQSmqNlE8SKAZFzDfxHfVILIIZ10sJ3OwIbcqSuiOjchkzNCboHev9o2YhgiUP8mxnLN24I6/3ghYdtQG5iUMpFBuCP9iKwLsfiLyeCp2rMnZgwX3NArGoxW1Ridl+BzLEVKa8KSxOqNmDdz0kFnxaLHhWEgAyZigWhHXL+pEDy2ozsDxv8vAzTnh7w5kcghqCaFmCT10of4iPIT2mRdPUh4HoCcVwBH/8Ac2kzUkEV5r3EfVSOvbAJa5NDyI0r2oDtWb1EClh+OoC3Pg7v/Bw7p939yI4rsRW2Y3lKh01eh7WpIRyKZqzyjjYgPdIvlaMWRqYuG7wWryYHsRM0sFolZiPvQ3jheIwSmSBPdkByG/B6Wi3RYiVmRX7GiAPiUCRisii8D+jZNKvPBrHCW1GY0bAz6WkDCtOaSyKQFsi4K5NqNiZtehN2Y5uAShETqolhBqJXpfdPuPsuWwAaRdHSkxdc11mPqkGnyY4pyKbpl1GyJ0Pel7yqBoFcF3zqno5f+d8ohYy9Sx7lzQpxo1eirluCDgt++00p6uxttrG4F/A39sJGZWZMfrcp6O6+5kaVzXJHAOj6DeSs8qw5o8oxAAAAAElFTkSuQmCC)
[<img alt="Mod Loader: Forge / NeoForge / Fabric" src="https://img.shields.io/badge/loader-Forge/NeoForge/Fabric-1976d2?style=flat-square"/>](https://neoforged.net)
![Side: Client](https://img.shields.io/badge/side-Client-28733b?style=flat-square)
![GitHub](https://img.shields.io/github/license/endorh/simple-config?style=flat-square)
[<img alt="Curse Forge" src="https://cf.way2muchnoise.eu/short_782653_downloads(4E4E4E-E04E14-E0E0E0-2D2D2D-E0E0E0).svg?badge_style=flat"/>](https://www.curseforge.com/minecraft/mc-mods/smart-completion)
[<img alt="Join the Discord" src="https://img.shields.io/discord/1017484317636710520?color=%235865F2&label=&labelColor=4e4e4e&logo=discord&logoColor=white&style=flat-square"/>](https://discord.gg/gqYVjBq65U)

## Smart Completion Mod

Better autocompletion for Minecraft commands (client-side).

### Usage
In order to type a command/name made up of several words, focus only on
typing a few initials of each of its words, without spaces in between.

For example, if you wanted to type `/gamerule doDaylightCycle true`, you could
type:
- `gr`, followed by `Ctrl+Space` or `Tab` to expand as `gamerule`

![gamerule](https://raw.githubusercontent.com/endorh/smart-completion/1.20%2B/images/%5Bg%5Dame%5Br%5Dule.png)
- `dc` or `ddc`, followed by `Ctrl+Space` or `Tab` to expand as `doDaylightCycle`

![doDaylightCycle](https://raw.githubusercontent.com/endorh/smart-completion/1.20%2B/images/%5Bd%5Do(D)aylight%5BC%5Dycle.png)
- `t`, followed by `Tab` on `Enter` to expand as `true`

---

Using `Ctrl+Space` instead of `Tab` also adds an additional space after
the completed word.
Pressing `Enter` only accepts a suggestion when the current command is
known to be invalid (if your text is red), so pressing `Enter` to execute
a valid command will always do so, even if there is a selected suggestion
in the list.

If you need to disambiguate between suggestions, you may type more than
one letter per word.
For example, to type `fireDamage` instead of `fallDamage`, which appears
first when typing `fd`, you may type `fid`.

![fireDamage](https://raw.githubusercontent.com/endorh/smart-completion/1.20%2B/images/%5Bf%5Dire%5BD%5Damage.png)

In general, you may type as much as you feel like of the start of each
word you want type, until the suggestion you want becomes the first one
in the list.

For example, you could start typing two initials for each word, such as
`garu` for `gamerule`.
Over time, you end up learning what's the minimum you really need to
type to get each command you frequently use.

Since the words you type don't need to be at the start of the name you're
typing, this is also helpful to discover options containing a certain word.

If your query doesn't match the initials of any option, it will also be
looked in between words, which is helpful when you don't know exactly what
you're looking for.

### Implementation
This mod is purely client-side. In order to provide better completions for
a command argument it sends two requests for completions to the server,
one with the partially typed argument (informed) and one without (blind).

The mod relies on the server replying with all possible suggestions to
the blind request.

Once both lists of suggestions have been received, the mod
then combines them, and uses its own algorithm to filter and sort the
suggestions.

If the server replies with suggestions for the informed request that
were not matched by the matching algorithm on the blind list, they will
also be suggested after any other matched suggestion, highlighted with
a different color (dark aqua by default).

This implementation should work well with any custom commands, added by
either mods or datapacks.

### Matching
Suggestions are matched to your partially typed arguments using two
different approaches:
- A (smart) match between *parts* of your query and the initials of the *parts*
  of the suggestion (`[g]ame[r]ule`)
- A (dumb) search of each *part* of your query in the suggestion, in order
  (`ga(me)r(ul)e`)

A suggestion will be shown if any of these approaches matches it, but
smart matches will be shown first.

#### Smart Matching
The smart matching algorithm splits both your query and all suggestion in
*parts* or *words*. Then, it tries to subsequently split each part of your
query in a way that can match the initials of parts of the suggestion in
order, skipping parts as necessary and disregarding case.

In general, you won't need to type different multiple words in your query,
but it could be useful if you want to enforce a specific split.

#### Dumb Matching
The dumb matching algorithm will only split your query in *parts*, not the
suggestion. Then, it will try to find each part of your query in the
suggestion in order, disregarding case.

This lets you find text within suggestions that may not be at the start of
a *part*/*word*.

### Word Splitting
Both matching algorithm described above are based in word splitting.
This mod recognizes the following ways to split words:
- `space characters`
- `camelCase`
- `snake_case`
- `kebab-case`
- `dot.case`
- `colon:delimiter`
- changes between letter characters and other non-letter characters,
  such as numbers

These word delimiters may be mixed in any way.

In addition, if a suggestion cannot be split by any of the above methods,
the mod will assume it's written in `flatcase` (like most Minecraft commands,
e.g., `gamerule`). In this case, the mod will use a list of known words
to attempt to split the flat case into fragments.

This list of words is defined by the
[`command_splitting.json`](https://github.com/endorh/smart-completion/blob/main/common/src/main/resources/assets/smartcompletion/smart-completion/command_splitting.json)
file, and can be overridden by resource packs (see
[wiki](https://github.com/endorh/smart-completion/wiki/Changing-the-List-of-Known-Words-used-to-Split-Commands)).
By default, it contains words used by Minecraft, Forge, Fabric and WorldEdit
commands, as well as a few more.

The algorithm to split `flatcase` can also swallow some suffix characters
after words, if not part of another word. By default, it will
swallow the `s` character after a word, assuming it's a plural form.
This feature is quite pointless and may be dropped at any time.

### Suggestion Sorting
Suggestions are sorted by the following criteria, in descending order of priority:
- Smart matches > dumb matches > unexpected server suggestions (`[d]o(D)aylight[C]ycle > sen(dC)ommandFeedback`)
- more part matches > less part matches (`[d]o[I]nsomnia > [di]sableRaids`)
- matches where a single query part could've matched more than one suggestion part
  are sorted first (`[d]o(D)aylight[C]ycle > [d]oWeather[C]ycle`)
- less suggestion parts > more suggestion parts (`[d]o[I]nsomnia > [d]o[I]mmediateRespawn`)
- earlier matched parts > later matched parts (`[g]ame[m]ode > default[g]ame[m]ode`)
- shorter suggestions > longer suggestions (`[t]p > [t]ell`)
- original order from the server (usually alphabetic) (`[f]all[D]amage > [f]ire[D]amage`)

### Suggestion Style
Suggestions are highlighted according to the styles defined in the
[`completion_style.json`](https://github.com/endorh/smart-completion/blob/1.20+/common/src/main/resources/assets/smartcompletion/smart-completion/completion_style.json)
file, which can be overridden by resource packs (see
[wiki](https://github.com/endorh/smart-completion/wiki/Customizing-the-Style-of-Suggestions)).

By default, matches inside a suggestion are highlighted in blue.
Alternative matches for a query part (`[d]o(D)aylight[C]ycle`),
dumb matches and unexpected server suggestions are all highlighted in dark aqua.

In addition, if a suggestion starts with a prefix of words, followed by a colon,
(i.e., a resource location namespace), it will be highlighted in dark gray unless
matched.

### Keyboard Shortcuts
This mod also lets you use `Ctrl+Space` and `Enter` to accept command
suggestions. The `Enter` key is only used to accept suggestions when
the currently typed command is invalid.

Using `Ctrl+Space` to accept a completion also inserts a space after it
so you can start typing the next argument. (It's definitely an intentional
feature and not the fruit of me being too lazy to suppress the `Space` key
event from being handled also by the input bar :) ).
