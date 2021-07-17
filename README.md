# the-worst-mindustry-plugin

This is yet another rewrite of a plugin for TWS server. This time around though, I decided to 
write it in kotlin. It turned out to be the best decision I ever made. 

## setup

Plugin is now using postgres sql so in order to use it you have to have it installed. When server 
is first run, it will probably crash due to invalid password. You have to go to a config that 
gets created automatically. Config file should look similar to this.
```json
{
  "database" : "mtest", 
  "password" : "helloThere", 
  "user" : "postgres",
}
```
Important things you have to set are user and password. Set them to values with which you log into postgres
console. If you installed postgres correctly and when by an instructions on their website, after typing `psql`
in your command prompt, postgres should ask you for a password and this exact password has to be put to the 
config. Once you opened the console, you have to use `create database <db_name>;` to create a database with a 
name you desire. This same name then has to be put inside `database field in config`.

Now you should be able to launch server with no errors.

## Features

Here you can commit your requests. I may or may not approve them. You can boost your chances by formatting 
suggestion correctly, so I don't have to do it. (and I will not)

- [x] database - plugin is using postgres database
  - [x] user account - every player has his own account and unique identifier
    - [x] password - user have right to maintain multiple accounts if he adds a password to them
    - [x] a custom name - user can change his name in game by command, plugin will remember his name
  - [x] bans - subnet and uuid bans
    - [ ] vpn - vpn detection with some free apy
  - [x] profile - player has his own profile that is viewable
    - [x] stats - plugin is counting player actions and saving then to database
  - [x] full control - modifying database can be done from server terminal, game or discord 
  - [x] /search - searching in a database can be performed by any player
  - [x] /mapmanager - command to manage maps. They ase stored in database and can be e
    enabled or disabled.
    - [ ] map stats - serves should track statistics to convey what maps should be removed.
- [x] configurations - plugin is highly configurable, all ranks can be defined by the owner of server
  - [x] /configure - gives ability to modify json config file from command like game and discord
    - [x] reloading - configuration can be reloaded while server is running
- [x] discord bot - plugin has connected discord bot
  - [x] live chat - connects discord channel on discord with in-game chat
  - [x] rank log - for rank change reports
  - [ ] command log - for command use logging
  - [x] linking - ability to link your discord account with plugin account 
- [x] ranks - way of administration
  - [x] customizable - player can choose witch rank to play with
  - [x] rank info - /ranks is in-game command that can show what ranks you obtained or yor progress towards obtaining them
  - [x] quests - you can obtain ranks by quests
    - [x] stats - all your stats in profile can be used as quest
    - [x] relations - rank ca be obtained only if you already have some ranks
    - [x] discord roles - ranks can be obtained only trough presence of discord role
    - [x] points - all yor stats are evaluated into total score
  - [x] pets - cosmetic accessory related to rank (particle trail that flies around you)
    - [ ] shooting pets - some pets can shoot projectiles on enemy  
  - [x] mounts - rank can grant you ability to transform into bigger unit
  - [x] color transition - some ranks use color transition (text smoothly interpolates colors)
  - [x] special permissions - rank can also grant you special permissions regarding commands and voting sessions
  - [ ] afk rank - when you are inactive for an amount of time, you will get marked afk
- [x] voting sessions - every player can open his own voting session
  - [x] hud - voting sessions are displayed in hud (message, time, vote state)
  - [x] a permissive vote - if player has permission fitting the command, session is lot easier to pass  
  - [x] /mute - mutes player for you
  - [x] /maps - command can <change|restart|end> the map or display available maps 
  - [x] votekick - marks player griefer. After mark happens, anyone can kick player without a voting session
    - [x] hammer - hammer also works as votekick command, vanilla version is completely replaced
  - [ ] /kickafk - kicks afk players
- [x] bundle - plugin supports language bundles
- [x] inspect - shows information about actions regarding tiles
- [x] /loadout - stores resource for later and adds special ways to spend them
  - [x] /buildcore - build core from loadout resources
  - [ ] /factory - build units from loadout resources and send them as backup
  - [x] /boost - spend loadout resources on special boosts like building speed (not sure if it is possible)
    
# documentation

## worst/config.json

```json
{
  "configPaths": {},
  "disabledGameCommands": [],
  "doubleTapSensitivity": 300,
  "gamemode": "survival",
  "inspectHistorySize": 5,
  "maturity": 18000000,
  "minBuildCost": 60.0,
  "testPenalty": 900000,
  "vpnApyKey": ""
}
```

The generic config file. Here you can specify config path map. Just specify the configuration name and map it to 
desired path so servers can share a common config. To see the list of configurable items use `configure` command.
`vpnApyKey` is backed by https://vpnapi.io so get the key here. If apy key is empty, you have only 100 checks per 
day. With the key you have 1000 which for most cases is enough.

## worst/databaseDriver/config.json

Config defines behavior of the database.

```json
{
  "database": "mtest",
  "multiplier": {
    "built": 100,
    "commands": 0,
    "deaths": 10,
    "destroyed": 5,
    "killed": 1,
    "messages": 0,
    "playTime": 100000,
    "played": 200,
    "silence": 10000000,
    "wins": 1000
  },
  "password": "",
  "user": "postgres"
}
```

I will not explain thi in detail. Each field should make sense except multipliers. These are related to 
`points` quest in ranks. Almost all properties will multiply the value except playTime and silence. 
These are dividers.

## worst/ranks/config.json

In his file you can define the ranks for players to achieve or use as administration tool. Lets first go 
over rank structure. Here is example rank:

```json
{
  "owner": {
    "color": "#D184FF #F1F1FF #84D1FF",
    "control": "Absolute",
    "description": {
      "default": "The guy that is paying for all of this."
    },
    "displayed": true,
    "kind": "Normal",
    "permanent": true,
    "perms": ["Scream"],
    "quest": {},
    "unit": "mega",
    "value": 0,
    "voteValue": 4,
    "unitRecharge": 30000,
    "pets": ["petName"]
  }
}
```
This is an example of high order rank for tom admins. The control is absolute means that player can 
do everything that you can do trough server terminal (as far as plugin goes). There are multiple types 
of control levels: 

- `None` is for players that are banned on the server. It disallows prayer from doing anything. Only send 
  messages to chat and spectate
  
- `Paralized` is similar to none but allows player to log in with `account` command. This prevents players 
  from playing with no account.
  
- `Minimal` is for newcomers, the players that are not verified yet. Player with this permission can use most 
  of the commands, but he cannot modify blocks of players with higher permission level.
  
- `Normal` is for verified players. They can do anything that does not affect other players status.

- `High` is for admins, Main feature is `setrank` and no command rate-limits

- `Absolute` already mentioned.

Somewhat less important is `color` property which determinate the color of the rank and user message. Multiple 
colors can be combined creating gradient. You have to use hex notation and separate colors with spaces.

`description` is what shows up when you use `/ranks <rankName>`. This should describe rank purpose, some story 
or whatever, you can leave it blanc. The description supports a bundle, that's why you have to write each
translation under the key. `default` key is fall back for players that are missing a translation.

`displayed` determinate whether rank should be appended to players name. This is useful for common ranks like 
newcomer or verified, which you generally don't want to show as it is spammy.

Another property is `kind`. There are only two variants, that is `Normal` for ranks that cannot be obtained by 
a quest and `Special` that can be.

`perms` denote some special permissions for a player. They mostly affect voting sessions but can also have 
other effects. Probably more to come.

- `Skip` allows a player to instantly use any command that would otherwise require vote session.

- `Scream` gives player ability to send uppercase messages.

- permissions that reduce vote session duration and make it succeed if there is more yes votes than no votes: 
  `VoteKick` = vokekick, `Maps` = maps, `Store` = loadout store, `Load` = loadout load, `BuildCore` = buildcore, 
  `Boost` = boost
  
- `quest` is most complicated property. It allows defining conditions under which player will obtain the rank, 
  if it is special. 

    - simple quests that just assert the amount of some actions are `built destroyed killed deaths played wins 
      messages commands playTime silence age rankCount rankTotalValue points`. You specify these as `"name": number`. 
      The time stamps has to be specified in milliseconds(3600000 = 1h). (`points` are calculated based of other 
      stats, and their significance which is also configured)
      
    - `ranks` specify which ranks player has to have to obtain the current one. These gets rid of repetition in 
      case you want to combine ranks.
      
    - `roles` if player have his discord account synced, this quest can set the discord role as requirement to 
      obtain the rank. Roles are continuous string where ';' are delimiters between role names.
      
- `unit` can be set to name of some in game unit and if player uses `/spawn mount` he will turn into that unit.
related property is `unitRecharge` which signifies the time since dismount in milliseconds that is required for 
  a mount to be used again.
  
- `voteValue` indicates how much is players vote worth. The total vote count required is also affected by this 
  property, when player is online.
  
- `pets` defines which pets the rank owns and will spawn when player joins the game.
  
## worst/bot/config.json

File contains discord bot configuration

```json
{
  "disabled": true,
  "permissions": {
    "execute": ["roleName", "otherRoleName"]
  },
  "prefix": "~",
  "token": "your bot token",
  "channels": {
    "chat": "channel id"
  }
}
```

`disabled` makes bot disabled so that plugin will not attempt to connect with a bot.

`permissions` are way to map the required roles to use commands. (`"command name": [..roles]`)

`channels` hold ides to guild channels that are used by the plugin. The options are `chat` ro bridge 
game chat with discord chat and `rankLog` to log the rank changes.

## worst/buildcore/config.json

```json
{
  "costs": {
    "copper": 1
  },
  "shipTravelTime": 180
}
```
`costs` specify resources needed to build the core.

`shipTravelTime` for how long it takes to deliver the core in seconds.

## worst/boost/config.json

```json
{
  "buildSpeed": {
    "cost": {
      "copper": 1
    },
    "duration": 30,
    "effects": {
      "buildSpeedMultiplier": 10000.0
    }
  }
}
```

`cost` resources required to activate the boost.

`duration` of the boost in seconds.

`effects` enumerate the boost effects on the game. If you want to know full list of options, 
add something random and error with options will show up.



