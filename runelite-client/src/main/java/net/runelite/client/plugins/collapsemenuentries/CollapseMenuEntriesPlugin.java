/*
 * Copyright (c) 2018, Sypanite <https://github.com/Sypanite>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.collapsemenuentries;

import net.runelite.client.eventbus.Subscribe;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.ColorUtil;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;

@Slf4j
@PluginDescriptor(
		name = "Collapse Menu Entries",
		description = "Collapse duplicate context menu entries on ground items, reducing clutter.",
		tags = {"collapse", "item", "context", "ground", "menu", "entries"}
)
public class CollapseMenuEntriesPlugin extends Plugin
{

	static class ItemOption
	{
		protected MenuEntry baseEntry = null;
		protected int numOccurrences = 1;
		protected boolean bProcessedInRebuild = false;
	}

	public final static String COUNT_PREFIX = " (x",
							   COUNT_SUFFIX = ")";

	public final static int GROUND_ITEM_OPTION_1 = 20,
						    GROUND_ITEM_OPTION_2 = 21,
						    GROUND_ITEM_OPTION_3 = 22,
							GROUND_ITEM_EXAMINE = 1004;

	@Inject
	private Client client;

	@Inject
	private CollapseMenuEntriesConfig config;

	private HashMap<Integer, ArrayList<ItemOption>> optionMap;

	@Override
	protected void startUp() throws Exception
	{
		optionMap = new HashMap<>();
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		MenuEntry[] menuEntries = client.getMenuEntries();
		int numOptionsCulled = buildOptionMap(menuEntries);

		if (numOptionsCulled > 0)
		{
			rebuildContextMenu(menuEntries, numOptionsCulled);
		}
	}

	private int buildOptionMap(MenuEntry[] menuEntries)
	{
		optionMap.clear();

		int numOptionsCulled = 0;

		for (int i = 0; i != menuEntries.length; i++)
		{
			MenuEntry entry = menuEntries[i];

			if (isGroundItemOption(entry) == false)
			{
				continue;
			}

			int itemID = entry.getIdentifier();

			if (optionMap.containsKey(itemID))
			{
				ArrayList<ItemOption> itemOptions = optionMap.get(itemID);
				boolean bFoundOption = false;

				for (ItemOption itemOption : itemOptions)
				{
					if (areEntriesEqual(entry, itemOption.baseEntry))
					{
						itemOption.numOccurrences++;
						numOptionsCulled++;
						bFoundOption = true;
						break;
					}
				}

				if (bFoundOption == false)
				{
					itemOptions.add(createItemOption(entry));
				}
			}
			else
			{
				ArrayList<ItemOption> newOptionList = new ArrayList<>();

				newOptionList.add(createItemOption(entry));
				optionMap.put(itemID, newOptionList);
			}
		}
		return numOptionsCulled;
	}

	private void rebuildContextMenu(MenuEntry[] menuEntries, int numOptionsCulled)
	{
		MenuEntry[] newMenuEntries = new MenuEntry[menuEntries.length - numOptionsCulled];
		int newEntryIndex = 0;

		for (int i = 0; i != menuEntries.length; i++)
		{
			MenuEntry entry = menuEntries[i];

			if (isGroundItemOption(entry))
			{
				int itemID = entry.getIdentifier();
				ArrayList<ItemOption> itemOptions = optionMap.get(itemID);

				for (ItemOption itemOption : itemOptions)
				{
					if (areEntriesEqual(entry, itemOption.baseEntry))
					{
						if (itemOption.bProcessedInRebuild == false)
						{
							if (itemOption.numOccurrences > 1 && config.showNumber())
							{
								String target = itemOption.baseEntry.getTarget();
								String duplicateCountString = ColorUtil.wrapWithColorTag(COUNT_PREFIX + itemOption.numOccurrences + COUNT_SUFFIX, config.collapsedColour());

								entry.setTarget(target + "</col>" + duplicateCountString);
							}
							itemOption.bProcessedInRebuild = true;
							newMenuEntries[newEntryIndex++] = entry;
						}
					}
				}
			}
			else
			{
				newMenuEntries[newEntryIndex++] = entry;
			}
		}

		client.setMenuEntries(newMenuEntries);
	}

	private boolean areEntriesEqual(MenuEntry a, MenuEntry b) {
		return a.getOption().equals(b.getOption()) && a.getTarget().equals(b.getTarget());
	}

	private ItemOption createItemOption(MenuEntry baseEntry)
	{
		ItemOption newOption = new ItemOption();
		newOption.baseEntry = baseEntry;
		return newOption;
	}

	private boolean isGroundItemOption(MenuEntry entry)
	{
		int type = entry.getType();
		return type == GROUND_ITEM_OPTION_1 || type == GROUND_ITEM_OPTION_2 || type == GROUND_ITEM_OPTION_3 || type == GROUND_ITEM_EXAMINE;
	}

	@Provides
	CollapseMenuEntriesConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CollapseMenuEntriesConfig.class);
	}
}