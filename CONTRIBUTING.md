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

This mod uses [Architectury](https://www.curseforge.com/minecraft/mc-mods/architectury-api)
to support Forge and Fabric.

The structure of the project is inspired by
[Distant Horizons](https://gitlab.com/jeseibel/minecraft-lod-mod/)
and uses [Manifold](http://manifold.systems) to support multiple Minecraft
versions with the same codebase.

Refer to the [Architectury Wiki](https://docs.architectury.dev/plugin:get_started)
or the [Distant Horizons Readme.md](https://gitlab.com/jeseibel/minecraft-lod-mod/-/tree/main#source-code-installation)
for more information on the structure of this project.

***

The Minecraft version of the project is specified by the `mcVersion` property
from `gradle.properties`.

Properties for each Minecraft version can be found within the
`versionProperties` folder.

***

This project is basically my experiment to find a project setup that I can use
to develop multi-loader and multi-version mods more efficiently.

Currently, running the Forge version from the IDE fails for some Minecraft versions.
Running with the Gradle task `runClient` sometimes is more reliable, but it can
still fail. This issue was introduced when the build logic was migrated to
Gradle's Kotlin DSL (73d229e25b63a858595c4e198a7b229ab459634b).

In addition, switching Minecraft versions sometimes fails due to a file
being locked by the IDE.

If you have any suggestions to improve the project structure, by all means,
please share them with me, either on [Discord](https://discord.gg/gqYVjBq65U) or
by creating an issue/PR.
