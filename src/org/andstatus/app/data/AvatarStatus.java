/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.data;

import org.andstatus.app.util.MyLog;

public enum AvatarStatus {
    NEW(1),
    LOADED(2),
    EXPIRED(3),
    SOFT_ERROR(4),
    HARD_ERROR(5),
    ABSENT(6),
    UNKNOWN(0);
    
    private int code;
    private AvatarStatus(int codeIn) {
        code = codeIn;
    }
    
    public String save() {
        return Integer.toString(code);
    }
    
    public static AvatarStatus load(String strCode) {
        try {
            return load(Integer.parseInt(strCode));
        } catch (NumberFormatException e) {
            MyLog.v("AvatarState", "Error converting '" + strCode + "'", e);
        }
        return UNKNOWN;
    }
    
    public static AvatarStatus load(int codeIn) {
        for (AvatarStatus tt : AvatarStatus.values()) {
            if (tt.code == codeIn) {
                return tt;
            }
        }
        return UNKNOWN;
    }
}