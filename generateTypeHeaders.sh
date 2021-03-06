#!/bin/bash
origDir=$(pwd)
mkdir -p src/jsMain/kotlin/externaljs # create the directories for the headers
cd src/jsMain/kotlin/externaljs || exit
mkdir -p {jquery,raphael,typescript}

#download typescript headers from the DefinitelyTyped repo
curl https://raw.githubusercontent.com/DefinitelyTyped/DefinitelyTyped/master/types/jquery/v1/index.d.ts -o jquery/jquery.d.ts 
curl https://raw.githubusercontent.com/DefinitelyTyped/DefinitelyTyped/master/types/jqueryui/index.d.ts -o jquery/jqueryui.d.ts
curl https://raw.githubusercontent.com/DefinitelyTyped/DefinitelyTyped/master/types/raphael/index.d.ts -o raphael/raphael.d.ts
curl https://raw.githubusercontent.com/microsoft/TypeScript/master/lib/lib.dom.d.ts -o typescript/lib.dom.d.ts

#generate Kotlin external function declaration headers from these, 
#with appropriate package names (to match the dir)
cd jquery || exit
dukat -p externaljs.jquery jquery.d.ts jqueryui.d.ts
cd ../raphael || exit
dukat -p externaljs.raphael raphael.d.ts
cd ../ts-lib || exit
dukat -p externaljs.typescript lib.dom.d.ts
cd $origDir || exit
