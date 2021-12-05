// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.module.inventory.ui;

import org.terasology.gestalt.entitysystem.event.Event;
import org.terasology.nui.Canvas;

public class InventoryCellRendered implements Event {
    private Canvas canvas;

    public InventoryCellRendered(Canvas canvas) {
        this.canvas = canvas;
    }

    public Canvas getCanvas() {
        return canvas;
    }
}
