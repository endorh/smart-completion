### Contributing

Feel free to contribute words in a Pull Request to the
[`command_splitting`](https://github.com/endorh/smart-completion/blob/main/common/src/main/resources/assets/smartcompletion/smart-completion/command_splitting.json)
file.

I will accept any reasonable request to add words associated with
other mods, or Minecraft related words. However, I won't accept a list of words
stripped from a dictionary, that's too inefficient, so don't waste your time on
that. You may override it with a resource pack if you're so inclined.

Any other type of contribution is also welcome. If you wish to change the
matching algorithm, either verify that it passes all the tests under
[`SmartCommandCompletionTest`](https://github.com/endorh/smart-completion/blob/main/common/src/test/java/endorh/smartcompletion/SmartCommandCompletionTest.java)
or create an issue proposing a change in behavior.

I'm willing to accept alternative matching algorithms too, although the mod
currently has no config of any kind, so that'd have to be fixed in some way
first to let players choose which to use.

### Development

The structure of the project is inspired by
[Distant Horizons](https://gitlab.com/jeseibel/minecraft-lod-mod/)
and uses [Manifold](http://manifold.systems) to support multiple Minecraft
versions with the same codebase.

Refer to the [Distant Horizons Readme.md](https://gitlab.com/jeseibel/minecraft-lod-mod/-/tree/main#source-code-installation)
for more information on the structure of this project.

Prior to Minecraft 26.1 this mod used [Architectury](https://www.curseforge.com/minecraft/mc-mods/architectury-api)
to support NeoForge and Fabric.
This is still the case in the `v2` branch.

In the latest `v3` branch, the Architectury setup has been replaced with
a simpler basic setup that simply relies on Loom and ModDevGradle separately
on two subprojects (`fabric` and `neoforge`), copying code and resources from
a `common` subproject.

The `common` subproject is compiled against the Minecraft sources provided by Loom
for Fabric.
Unlike the new NeoForge setup, this still requires you to run the `genSources` task
to benefit from development sources for Minecraft in the `common` project.

There are still some limitations which I may address if they bother me enough in
the future:
- No support for loader-specific mixins
- No support for multiple source sets
- No support for split sides on fabric
- No proper support for access wideners in loader-specific code
- No support for interface injection (you can use mixins for that)
- No support for IDE run configurations (just use Gradle tasks, it's always been more consistent for me)
- Common sources are not copied to the per-loader JARs
- Common classes are not properly synced and may become stale after
  removals/renames. A clean build solves this, but a proper solution that
  plays nice with the configuration cache is needed.
- Some packages from the Fabric loader are still accessible to the `common` subproject.
  If used, they will break the NeoForge build. Ideally, Gradle could hide these from the classpath to
  prevent accidents.

In general, the current setup is very ad-hoc, so you may need to be cautious if you
want to use it as inspiration.
I didn't have the motivation for polishing it just yet.

While the current approach of copying compiled classes from the `common` project is rather inelegant,
any attempt to reference them as a proper dependency resulted in headaches to get NeoForge to load
the common classes with the right classloader in dev runs.

***

The Minecraft version of the project is specified by the `mcVersion` property
from `gradle.properties`.

Properties for each Minecraft version can be found within the
`versionProperties` folder.

***

This project is basically my experiment to find a project setup that I can use
to develop multi-loader and multi-version mods more efficiently.

Switching Minecraft versions sometimes fails due to a file
being locked by the IDE.

If you have any suggestions to improve the project structure, by all means,
please share them with me, either on [Discord](https://discord.gg/gqYVjBq65U) or
by creating an issue/PR.
