package moze_intel.projecte.emc;

import com.google.common.collect.Maps;
import moze_intel.projecte.PECore;
import moze_intel.projecte.api.event.EMCRemapEvent;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.emc.arithmetics.HiddenFractionArithmetic;
import moze_intel.projecte.emc.arithmetics.IValueArithmetic;
import moze_intel.projecte.emc.collector.DumpToFileCollector;
import moze_intel.projecte.emc.collector.IExtendedMappingCollector;
import moze_intel.projecte.emc.collector.IntToFractionCollector;
import moze_intel.projecte.emc.collector.WildcardSetValueFixCollector;
import moze_intel.projecte.emc.generators.FractionToIntGenerator;
import moze_intel.projecte.emc.generators.IValueGenerator;
import moze_intel.projecte.emc.json.NSSItem;
import moze_intel.projecte.emc.json.NormalizedSimpleStack;
import moze_intel.projecte.emc.mappers.APICustomConversionMapper;
import moze_intel.projecte.emc.mappers.APICustomEMCMapper;
import moze_intel.projecte.emc.mappers.CraftingMapper;
import moze_intel.projecte.emc.mappers.CustomEMCMapper;
import moze_intel.projecte.emc.mappers.IEMCMapper;
import moze_intel.projecte.emc.mappers.OreDictionaryMapper;
import moze_intel.projecte.emc.mappers.SmeltingMapper;
import moze_intel.projecte.emc.mappers.customConversions.CustomConversionMapper;
import moze_intel.projecte.emc.pregenerated.PregeneratedEMC;
import moze_intel.projecte.playerData.Transmutation;
import moze_intel.projecte.utils.PrefixConfiguration;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.commons.lang3.math.Fraction;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EMCMapper 
{
	public static final Map<SimpleStack, Integer> emc = new LinkedHashMap<>();

	public static double covalenceLoss = ProjectEConfig.difficulty.covalenceLoss;

	public static void map()
	{
		List<IEMCMapper<NormalizedSimpleStack, Integer>> emcMappers = Arrays.asList(
				new OreDictionaryMapper(),
				APICustomEMCMapper.instance,
				new CustomConversionMapper(),
				new CustomEMCMapper(),
				new CraftingMapper(),
				new moze_intel.projecte.emc.mappers.FluidMapper(),
				new SmeltingMapper(),
				new APICustomConversionMapper()
		);
		SimpleGraphMapper<NormalizedSimpleStack, Fraction, IValueArithmetic<Fraction>> mapper = new SimpleGraphMapper<>(new HiddenFractionArithmetic());
		IValueGenerator<NormalizedSimpleStack, Integer> valueGenerator = new FractionToIntGenerator<>(mapper);
		IExtendedMappingCollector<NormalizedSimpleStack, Integer, IValueArithmetic<Fraction>> mappingCollector = new IntToFractionCollector<>(mapper);
		mappingCollector = new WildcardSetValueFixCollector<>(mappingCollector);

		Configuration config = new Configuration(new File(PECore.CONFIG_DIR, "mapping.cfg"));
		config.load();

		if (config.getBoolean("dumpEverythingToFile", "general", false,"Want to take a look at the internals of EMC Calculation? Enable this to write all the conversions and setValue-Commands to config/ProjectE/mappingdump.json")) {
			mappingCollector = new DumpToFileCollector<>(new File(PECore.CONFIG_DIR, "mappingdump.json"), mappingCollector);
		}

		boolean shouldUsePregenerated = config.getBoolean("pregenerate", "general", false, "When the next EMC mapping occurs write the results to config/ProjectE/pregenerated_emc.json and only ever run the mapping again" +
						" when that file does not exist, this setting is set to false, or an error occurred parsing that file.");

		Map<NormalizedSimpleStack, Integer> graphMapperValues;
		if (shouldUsePregenerated && PECore.PREGENERATED_EMC_FILE.canRead() && PregeneratedEMC.tryRead(PECore.PREGENERATED_EMC_FILE, graphMapperValues = new HashMap<>()))
		{
			PECore.LOGGER.info(String.format("Loaded %d values from pregenerated EMC File", graphMapperValues.size()));
		}
		else
		{


			SimpleGraphMapper.setLogFoundExploits(config.getBoolean("logEMCExploits", "general", true,
					"Log known EMC Exploits. This can not and will not find all possible exploits. " +
							"This will only find exploits that result in fixed/custom emc values that the algorithm did not overwrite. " +
							"Exploits that derive from conversions that are unknown to ProjectE will not be found."
			));

			PECore.LOGGER.info("Starting to collect Mappings...");
			for (IEMCMapper<NormalizedSimpleStack, Integer> emcMapper : emcMappers)
			{
				try
				{
					if (config.getBoolean(emcMapper.getName(), "enabledMappers", emcMapper.isAvailable(), emcMapper.getDescription()) && emcMapper.isAvailable())
					{
						DumpToFileCollector.currentGroupName = emcMapper.getName();
						emcMapper.addMappings(mappingCollector, new PrefixConfiguration(config, "mapperConfigurations." + emcMapper.getName()));
						PECore.LOGGER.info("Collected Mappings from " + emcMapper.getClass().getName());
					}
				} catch (Exception e)
				{
					PECore.LOGGER.fatal("Exception during Mapping Collection from Mapper {}. PLEASE REPORT THIS! EMC VALUES MIGHT BE INCONSISTENT!", emcMapper.getClass().getName());
					e.printStackTrace();
				}
			}
			DumpToFileCollector.currentGroupName = "NSSHelper";
			NormalizedSimpleStack.addMappings(mappingCollector);

			PECore.LOGGER.info("Mapping Collection finished");
			mappingCollector.finishCollection();

			PECore.LOGGER.info("Starting to generate Values:");

			config.save();

			graphMapperValues = valueGenerator.generateValues();
			PECore.LOGGER.info("Generated Values...");

			filterEMCMap(graphMapperValues);

			if (shouldUsePregenerated) {
				//Should have used pregenerated, but the file was not read => regenerate.
				try
				{
					PregeneratedEMC.write(PECore.PREGENERATED_EMC_FILE, graphMapperValues);
					PECore.LOGGER.info("Wrote Pregen-file!");
				} catch (IOException e)
				{
					e.printStackTrace();
				}
			}
		}


		for (Map.Entry<NormalizedSimpleStack, Integer> entry: graphMapperValues.entrySet()) {
			NSSItem normStackItem = (NSSItem)entry.getKey();
			Item obj = Item.REGISTRY.getObject(new ResourceLocation(normStackItem.itemName));
			if (obj != null)
			{
				emc.put(new SimpleStack(obj.getRegistryName(), normStackItem.damage), entry.getValue());
			} else {
				PECore.LOGGER.warn("Could not add EMC value for {}|{}. Can not get ItemID!", normStackItem.itemName, normStackItem.damage);
			}
		}

		MinecraftForge.EVENT_BUS.post(new EMCRemapEvent());
		Transmutation.cacheFullKnowledge();
		FuelMapper.loadMap();
	}

	private static void filterEMCMap(Map<NormalizedSimpleStack, Integer> map) {
		map.entrySet().removeIf(e -> !(e.getKey() instanceof NSSItem)
										|| ((NSSItem) e.getKey()).damage == OreDictionary.WILDCARD_VALUE
										|| e.getValue() <= 0);
	}

	public static boolean mapContains(SimpleStack key)
	{
		return emc.containsKey(key);
	}

	public static int getEmcValue(SimpleStack stack)
	{
		return emc.get(stack);
	}

	public static void clearMaps() {
		emc.clear();
	}
}
