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
