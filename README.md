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
  "verbose" : false
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
- [x] configurations - plugin is highly configurable, all ranks can be defined by the owner of server
  - [x] /configure - gives ability to modify json config file from command like game and discord
    - [x] reloading - configuration can be reloaded while server is running
- [x] discord bot - plugin has connected discord bot
  - [ ] live chat - connects discord channel on discord with in-game chat
  - [ ] error log - discord channel for logging errors
  - [ ] rank log - for rank change reports
  - [ ] command log - for command use logging
  - [ ] maps - for map suggestions, downloading and voting  
  - [x] linking - ability to link your discord account with plugin account 
- [x] ranks - way of administration
  - [x] customizable - player can choose witch rank to play with
  - [x] rank info - /ranks is in-game command that can show what ranks you obtained or yor progress towards obtaining them
  - [x] quests - you can obtain ranks by quests
    - [x] stats - all your stats in profile can be used as quest
    - [x] relations - rank ca be obtained only if you already have some ranks
    - [x] discord roles - ranks can be obtained only trough presence of discord role
    - [x] points - all yor stats are evaluated into total score
  - [ ] pets - cosmetic accessory related to rank (particle trail that flies around you)
    - [ ] shooting pets - some pets can shoot projectiles on enemy  
  - [x] mounts - rank can grant you ability to transform into bigger unit
  - [x] color transition - some ranks use color transition (text smoothly interpolates colors)
  - [x] special permissions - rank can also grant you special permissions regarding commands and voting sessions
  - [ ] afk rank - when you are inactive for an amount of time, you will get marked afk
- [x] voting sessions - every player can open his own voting session
  - [x] hud - voting sessions are displayed in hud (message, time, vote state)
  - [x] a permissive vote - if player has permission fitting the command, session is lot easier to pass  
  - [ ] /mute - mutes player for you
  - [ ] /maps - command can <change|restart|end> the map or display available maps 
  - [x] votekick - marks player griefer. After mark happens, anyone can kick player without a voting session
    - [x] hammer - hammer also works as votekick command, vanilla version is completely replaced
  - [ ] /kickafk - kicks afk players
- [x] bundle - plugin supports language bundles
- [ ] inspect - shows information about actions regarding tiles
- [ ] /loadout - stores resource for later and adds special ways to spend them
  - [ ] /buildcore - build core from loadout resources
  - [ ] /factory - build units from loadout resources and send them as backup
  - [ ] /boost - spend loadout resources on special boosts like building speed (not sure if it is possible)
    

