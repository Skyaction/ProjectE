package moze_intel.projecte.gameObjs.items.armor;

import moze_intel.projecte.gameObjs.ObjHandler;
import moze_intel.projecte.handlers.PlayerChecks;
import moze_intel.projecte.handlers.PlayerTimers;
import moze_intel.projecte.utils.ChatHelper;
import moze_intel.projecte.utils.KeyHelper;
import moze_intel.projecte.utils.PlayerHelper;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.ISpecialArmor;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import thaumcraft.api.IGoggles;
import thaumcraft.api.nodes.IRevealer;

import java.util.List;

@Optional.InterfaceList(value = {@Optional.Interface(iface = "thaumcraft.api.nodes.IRevealer", modid = "Thaumcraft"), @Optional.Interface(iface = "thaumcraft.api.IGoggles", modid = "Thaumcraft")})
public class GemArmor extends ItemArmor implements ISpecialArmor, IRevealer, IGoggles
{
	public GemArmor(int armorType)
	{
		super(ArmorMaterial.DIAMOND, 0, armorType);
		this.setCreativeTab(ObjHandler.cTab);
		this.setUnlocalizedName("pe_gem_armor_"+armorType);
		this.setHasSubtypes(false);
		this.setMaxDamage(0);
	}

	@Override
	public void onArmorTick(World world, EntityPlayer player, ItemStack stack)
	{
		if (world.isRemote)
		{
			if (stack.getItem() == ObjHandler.gemChest)
			{
				int x = (int) Math.floor(player.posX);
				int y = (int) (player.posY - player.getYOffset());
				int z = (int) Math.floor(player.posZ);
				BlockPos pos = new BlockPos(x, y, z);

				Block b = world.getBlockState(pos.offsetDown()).getBlock();
		
				if ((b.equals(Blocks.water) || b.equals(Blocks.flowing_water) || b.equals(Blocks.lava) || b.equals(Blocks.flowing_lava)) && world.isAirBlock(pos))
				{
					if (!player.isSneaking())
					{
						player.motionY = 0.0d;
						player.fallDistance = 0.0f;
						player.onGround = true;
					}
				}
			}
		}
		else
		{
			Item armor = stack.getItem();
			EntityPlayerMP playerMP = (EntityPlayerMP) player;
			
			if (armor == ObjHandler.gemFeet)
			{
				if (!playerMP.capabilities.allowFlying)
				{
					enableFlight(playerMP);
				}

				if (isStepAssistEnabled(stack))
				{
					if (playerMP.stepHeight != 1.0f)
					{
						playerMP.stepHeight = 1.0f;
						PlayerHelper.updateClientStepHeight(playerMP, 1.0F);

						PlayerChecks.addPlayerStepChecks(playerMP);
					}
				}
			}
			else if (armor == ObjHandler.gemLegs)
			{
				player.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 1, 4));
				
				if (!player.isSneaking())
				{
					player.addPotionEffect(new PotionEffect(Potion.jump.id, 1, 4));
				}
			}
			else if (armor == ObjHandler.gemChest)
			{
				PlayerTimers.activateFeed(playerMP);

				if (player.getFoodStats().needFood() && PlayerTimers.canFeed(playerMP))
				{
					player.getFoodStats().addStats(2, 10);
				}
				
				if (!player.isImmuneToFire())
				{
					PlayerHelper.setPlayerFireImmunity(player, true);
					PlayerChecks.addPlayerFireChecks(playerMP);
				}
			}
			else if (armor == ObjHandler.gemHelmet)
			{
				PlayerTimers.activateHeal(playerMP);

				if (player.getHealth() < player.getMaxHealth() && PlayerTimers.canHeal(playerMP))
				{
					player.heal(2.0F);
				}
				
				if (isNightVisionEnabled(stack))
				{
					if (world.getLight(new BlockPos(player)) < 10)
					{
						player.addPotionEffect(new PotionEffect(Potion.nightVision.id, 220, 0));
					}
					else if (player.isPotionActive(Potion.nightVision.id))
					{
						player.removePotionEffect(Potion.nightVision.id);
					}
				}
				else if (player.isPotionActive(Potion.nightVision.id))
				{
					player.removePotionEffect(Potion.nightVision.id);
				}
				
				if (player.isInWater())
				{
					player.setAir(300);
				}
			}
		}
	}
	
	public void enableFlight(EntityPlayerMP playerMP)
	{
		if (playerMP.capabilities.isCreativeMode)
		{
			return;
		}
		
		PlayerHelper.updateClientFlight(playerMP, true);
		PlayerChecks.addPlayerFlyChecks(playerMP);
	}
	
	@Override
	public ArmorProperties getProperties(EntityLivingBase player, ItemStack armor, DamageSource source, double damage, int slot) 
	{
		if (source.isExplosion())
		{
			return new ArmorProperties(1, 1.0D, 750);
		}

		if (slot == 0 && source == DamageSource.fall)
		{
			return new ArmorProperties(1, 1.0D, 15);
		}
		
		if (slot == 0 || slot == 3)
		{
			return new ArmorProperties(0, 0.2D, 400);
		}

		return new ArmorProperties(0, 0.3D, 500);
	}

	@Override
	public int getArmorDisplay(EntityPlayer player, ItemStack armor, int slot) 
	{
		if (slot == 0 || slot == 3)
		{
			return 4;
		}
		
		return 6;
	}

	@Override
	public void damageArmor(EntityLivingBase entity, ItemStack stack, DamageSource source, int damage, int slot) 
	{
	}
	
	public static void toggleStepAssist(ItemStack boots, EntityPlayer player)
	{
		if (!boots.hasTagCompound())
		{
			boots.setTagCompound(new NBTTagCompound());
		}
		
		boolean value;
		
		if (boots.getTagCompound().hasKey("StepAssist"))
		{
			boots.getTagCompound().setBoolean("StepAssist", !boots.getTagCompound().getBoolean("StepAssist"));
			value = boots.getTagCompound().getBoolean("StepAssist");
		}
		else
		{
			boots.getTagCompound().setBoolean("StepAssist", false);
			value = false;
		}

		EnumChatFormatting e = value ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
		String s = value ? "pe.gem.enabled" : "pe.gem.disabled";
		player.addChatMessage(new ChatComponentTranslation("pe.gem.stepassist_tooltip")
				.appendSibling(ChatHelper.modifyColor(new ChatComponentTranslation(s), e)));
	}
	
	public static void toggleNightVision(ItemStack helm, EntityPlayer player)
	{
		if (!helm.hasTagCompound())
		{
			helm.setTagCompound(new NBTTagCompound());
		}
		
		boolean value;
		
		if (helm.getTagCompound().hasKey("NightVision"))
		{
			helm.getTagCompound().setBoolean("NightVision", !helm.getTagCompound().getBoolean("NightVision"));
			value = helm.getTagCompound().getBoolean("NightVision");
		}
		else
		{
			helm.getTagCompound().setBoolean("NightVision", false);
			value = false;
		}

		EnumChatFormatting e = value ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
		String s = value ? "pe.gem.enabled" : "pe.gem.disabled";
		player.addChatMessage(new ChatComponentTranslation("pe.gem.nightvision_tooltip")
				.appendSibling(ChatHelper.modifyColor(new ChatComponentTranslation(s), e)));
	}
	
	public static boolean isStepAssistEnabled(ItemStack boots)
	{
		return !boots.hasTagCompound() || !boots.getTagCompound().hasKey("StepAssist") || boots.getTagCompound().getBoolean("StepAssist");

	}
	
	public static boolean isNightVisionEnabled(ItemStack helm)
	{
		return !helm.hasTagCompound() || !helm.getTagCompound().hasKey("NightVision") || helm.getTagCompound().getBoolean("NightVision");

	}
	
	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean bool) 
	{
		if (stack != null)
		{
			if (stack.getItem() == ObjHandler.gemFeet)
			{
				if (KeyHelper.getArmorEffectsKeyCode() >= 0 && KeyHelper.getArmorEffectsKeyCode() < Keyboard.getKeyCount())
				{
					list.add(String.format(
							StatCollector.translateToLocal("pe.gem.stepassist.prompt"), Keyboard.getKeyName(KeyHelper.getArmorEffectsKeyCode())));
				}

				EnumChatFormatting e = isStepAssistEnabled(stack) ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
				String s = isStepAssistEnabled(stack) ? "pe.gem.enabled" : "pe.gem.disabled";
				list.add(StatCollector.translateToLocal("pe.gem.stepassist_tooltip") + " "
						+ e + StatCollector.translateToLocal(s));
			}
			else if (stack.getItem() == ObjHandler.gemHelmet)
			{
				if (KeyHelper.getArmorEffectsKeyCode() >= 0 && KeyHelper.getArmorEffectsKeyCode() < Keyboard.getKeyCount())
				{
					list.add(String.format(
							StatCollector.translateToLocal("pe.gem.nightvision.prompt"), Keyboard.getKeyName(KeyHelper.getArmorEffectsKeyCode())));
				}

				EnumChatFormatting e = isStepAssistEnabled(stack) ? EnumChatFormatting.GREEN : EnumChatFormatting.RED;
				String s = isStepAssistEnabled(stack) ? "pe.gem.enabled" : "pe.gem.disabled";
				list.add(StatCollector.translateToLocal("pe.gem.nightvision_tooltip") + " "
						+ e + StatCollector.translateToLocal(s));
			}
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public String getArmorTexture (ItemStack stack, Entity entity, int slot, String type)
	{
		char index = this.armorType == 2 ? '2' : '1';
		return "projecte:textures/armor/gem_"+index+".png";
	}

	@Override
	@Optional.Method(modid = "Thaumcraft")
	public boolean showIngamePopups(ItemStack stack, EntityLivingBase player) 
	{
		return true;
	}

	@Override
	@Optional.Method(modid = "Thaumcraft")
	public boolean showNodes(ItemStack stack, EntityLivingBase player) 
	{
		return true;
	}
}
