/*Copyright 2017 Viktor Degtyarev

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/

package net.callrec.library.fix;

/**
 * Created by Viktor Degtyarev on 16.10.17
 * E-mail: viktor@degtyarev.biz
 */
public class CallRecorderFix {
    public static native int load();
    public static native int startFix(int i2);
    public static native int startFix7();
    public static native int stopFix();
    static {
        LibLoader.loadLib();
    }
}
