# EternalJukebox

The source files for the EternalJukebox, a rehosting of the Infinite Jukebox.  
This repo contains everything you need to host the EternalJukebox on your own server!  

You can visit the official site [here](https://eternalbox.dev/), 
in case you want to mess around with it without doing all the hard stuff.  

## External Dependencies Which Require Manual Setup

* Java 8 or higher
* [Youtube-dl](https://youtube-dl.org/)
* [ffmpeg](https://ffmpeg.org/) - Easily available in most Linux package managers 
* [NodeJs](https://nodejs.org/en/) - for running Dukat, see below. This project uses v12.18.2 as of writing. 
  I used a prebuilt package from [here](https://github.com/nodesource/distributions).
* [Dukat](https://github.com/kotlin/dukat) - converts TypeScript type annotations for JavaScript into Kotlin external function declarations.
  TLDR lets you use jQuery and other common js dependencies in Kotlin/JS in a well-typed way.
  Once you have Nodejs installed, doing `npm -g install dukat` should suffice.
* [type definitions for javascript dependencies](https://github.com/DefinitelyTyped/DefinitelyTyped) 
  You don't need the whole repo; just download the definition files that are specific to the js dependencies being used. 
  Current JavaScript npm dependencies are:
    - [jQuery version 1](https://github.com/DefinitelyTyped/DefinitelyTyped/blob/master/types/jquery/v1/index.d.ts)
    - [jQueryUI](https://github.com/DefinitelyTyped/DefinitelyTyped/blob/master/types/jqueryui/index.d.ts), 
    
  but this is subject to change.

First you need to get the type headers, so that EternalJukebox knows what types are returned by its Javascript libraries:

```shell script

mkdir -p src/jsMain/kotlin/externaljs # create the directory for the headers
cd src/jsMain/kotlin/externaljs

#download typescript headers from the DefinitelyTyped repo
curl http://https://github.com/DefinitelyTyped/DefinitelyTyped/blob/master/types/jquery/v1/index.d.ts -o jquery.d.ts 
curl https://github.com/DefinitelyTyped/DefinitelyTyped/blob/master/types/jqueryui/index.d.ts -o jqueryui.d.ts

#generate Kotlin external fun declaration headers from these, with the package name 'externaljs' (to match the dir)
dukat -p externaljs jquery.d.ts jqueryui.d.ts
``` 

## Building

You'll need a [JDK](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html), 
and [Jekyll](https://jekyllrb.com/).

1. Get these project files:

Grab a [stable release](https://github.com/medavox/EternalJukebox/releases)

or get the most recent, bleeding-edge version (no guarantees about stability!)

```shell script
git clone git@github.com:medavox/EternalJukebox.git
```

or
```shell script
git clone https://github.com/medavox/EternalJukebox.git
```

change directory into the cloned git repo

From there, building in Gradle is simple; just run `./gradlew clean shadowJar` from the project file directory.
That should produce a jar file in `build/libs` that will work for you.

In addition, you'll need to build the Jekyll webpages, which can be done by running `jekyll build --source _web --destination web`


## Configuring
First thing to do is create a new file called either `config.yaml` or `config.json` 
(YAML tends to be easier to write, but takes up slightly more space), 
then open it with notepad/notepad++ on Windows and whatever text editor you like on Linux (for example nano: `nano config.json`)

Now you should go to https://developer.spotify.com/my-applications/ and log in to your spotify account.  
Then click the "Create an app" button and a new page should popup.   
There give it a name and description and click create.   
It should send you to the new app's page, the only thing you need from here is your Client ID and Client Secret  
(Note: Never share these with anyone!)  

You will also need a Youtube Data API key, which you can find about how to obtain [here](https://developers.google.com/youtube/v3/getting-started).

There are a variety of config options (documentation coming soon) that allow most portions of the EternalJukebox to be configured, and these can be entered here.

## Starting the server:

First you need to open the Terminal or Command Prompt.  
Then make sure its running in the folder that your EternalJukebox.jar is in, once again to do this use the `cd` command.  
Then execute the jar with `java -jar EternalJukebox.jar`

If everything went right it should say `Listening at http://0.0.0.0:11037`  

you should now be able to connect to it with a browser through http://localhost:11037  

Congrats you did it!  