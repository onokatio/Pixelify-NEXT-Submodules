# Bundled SQLite

The ZIP contains the Spoofing scripts, WebUI, and SQLite 3.53.3 for ARM, ARM64, x86, and x86-64. The installer selects the binary for the device and runs an in-memory query before enabling the module. The Phenotype patcher targets the legacy `Flags` and `FlagOverrides` tables.

The prebuilts in `module/addon` come from [sqlite3 for Magisk, commit `9b089c4`](https://github.com/Magisk-Modules-Alt-Repo/sqlite3/tree/9b089c449472fbf71edd9fbabf93a7c3db6a0ff1/system/bin). [SQLite public-domain notice](https://www.sqlite.org/copyright.html)
