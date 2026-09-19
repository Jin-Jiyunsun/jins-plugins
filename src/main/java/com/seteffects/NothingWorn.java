package com.seteffects;

import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Node;
import net.runelite.api.gameval.InventoryID;

/**
 * Stands in for the worn equipment container when the client has none. It has none until something
 * has been worn or removed since logging in, so opening the equipment stats with nothing on returned
 * null and the list never drew. Nothing worn is a perfectly good state to describe.
 */
final class NothingWorn implements ItemContainer
{
	static final ItemContainer INSTANCE = new NothingWorn();

	private static final Item[] NO_ITEMS = new Item[0];

	private NothingWorn()
	{
	}

	@Override
	public int getId()
	{
		return InventoryID.WORN;
	}

	@Override
	public Item[] getItems()
	{
		return NO_ITEMS;
	}

	@Override
	public Item getItem(int slot)
	{
		return null;
	}

	@Override
	public boolean contains(int itemId)
	{
		return false;
	}

	@Override
	public int count(int itemId)
	{
		return 0;
	}

	@Override
	public int size()
	{
		return 0;
	}

	@Override
	public int count()
	{
		return 0;
	}

	@Override
	public int find(int itemId)
	{
		return -1;
	}

	@Override
	public Node getNext()
	{
		return null;
	}

	@Override
	public Node getPrevious()
	{
		return null;
	}

	@Override
	public long getHash()
	{
		return 0;
	}
}
