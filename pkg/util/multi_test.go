/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package util

import "testing"

func TestMulti1(t *testing.T) {

    m := MultiTool{}

    counter := 0

    m.Run(func() (b bool, e error) {
        counter++
        return true, nil
    })
    m.Run(func() (b bool, e error) {
        counter++
        return true, nil
    })

    if counter != 2 {
        t.Errorf("Did not invoke all operations, expected: %d, got: %d", 2, counter)
    }

}
