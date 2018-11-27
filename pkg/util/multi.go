/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package util

import "go.uber.org/multierr"

type MultiTool struct {
    Continue bool
    Changed  bool
    Error    error
}

func (m *MultiTool) Run(operation func() (bool, error)) {

    if m.Error != nil && !m.Continue {
        return
    }

    changed, err := operation()

    if err != nil {
        if m.Error != nil {
            m.Error = multierr.Append(m.Error, err)
        } else {
            m.Error = err
        }
    } else {
        if changed {
            m.Changed = true
        }
    }

}

func (m *MultiTool) Reset() {
    m.Changed = false
    m.Error = nil
}

func (m *MultiTool) Return() (bool, error) {
    return m.Changed, m.Error
}
