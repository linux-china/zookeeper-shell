/*
 * Copyright 2011-2012 the original author or authors.
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
package org.springframework.shell.core;

import org.springframework.shell.core.annotation.CliAvailabilityIndicator;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.shell.event.ParseResult;
import org.springframework.shell.support.logging.HandlerUtils;
import org.springframework.shell.support.util.ExceptionUtils;
import org.springframework.shell.support.util.NaturalOrderComparator;
import org.springframework.shell.support.util.OsUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;

/**
 * Default implementation of {@link Parser}.
 *
 * @author Ben Alex
 * @since 1.0
 */
public class SimpleParser implements Parser {

	// Constants
	private static final Logger LOGGER = HandlerUtils.getLogger(SimpleParser.class);
	private static final Comparator<Object> COMPARATOR = new NaturalOrderComparator<Object>();

	// Fields
	private final Object mutex = new Object();
	private final Set<Converter<?>> converters = new HashSet<Converter<?>>();
	private final Set<CommandMarker> commands = new HashSet<CommandMarker>();
	private final Map<String, MethodTarget> availabilityIndicators = new HashMap<String, MethodTarget>();

	private MethodTarget getAvailabilityIndicator(final String command) {
		return availabilityIndicators.get(command);
	}

	/**
	 * get all mandatory options keys. For the options with multiple keys, the
	 * keys will be in one row.
	 *
	 * @param cliOptions options
	 * @return mandatory options keys
	 */
	private List<List<String>> getMandatoryOptionsKeys(Collection<CliOption> cliOptions) {
		return getOptionsKeys(cliOptions, false);
	}

	/**
	 * get all options key.
	 *
	 * @param cliOptions
	 * @param includeOptionalOptions
	 * @return options keys
	 */
	private List<List<String>> getOptionsKeys(Collection<CliOption> cliOptions, boolean includeOptionalOptions) {
		List<List<String>> optionsKeys = new ArrayList<List<String>>();
		for (CliOption option : cliOptions) {
			if (includeOptionalOptions) {
				List<String> keys = new ArrayList<String>();
				keys.addAll(Arrays.asList(option.key()));
				optionsKeys.add(keys);
			}
			else if (option.mandatory()) {
				List<String> keys = new ArrayList<String>();
				keys.addAll(Arrays.asList(option.key()));
				optionsKeys.add(keys);
			}
		}
		return optionsKeys;
	}


	public ParseResult parse(final String rawInput) {
		synchronized (mutex) {
			Assert.notNull(rawInput, "Raw input required");
			final String input = normalise(rawInput);

			// Locate the applicable targets which match this buffer
			final Collection<MethodTarget> matchingTargets = locateTargets(input, true, true);
			if (matchingTargets.isEmpty()) {
				// Before we just give up, let's see if we can offer a more informative message to the user
				// by seeing the command is simply unavailable at this point in time
				matchingTargets.addAll(locateTargets(input, true, false));
				if (matchingTargets.isEmpty()) {
					commandNotFound(LOGGER, input);
				}
				else {
					LOGGER.warning("Command '"
							+ input
							+ "' was found but is not currently available (type 'help' then ENTER to learn about this command)");
				}
				return null;
			}
			if (matchingTargets.size() > 1) {
				LOGGER.warning("Ambigious command '" + input + "' (for assistance press "
						+ AbstractShell.completionKeys + " or type \"hint\" then hit ENTER)");
				return null;
			}
			MethodTarget methodTarget = matchingTargets.iterator().next();

			// Argument conversion time
			Annotation[][] parameterAnnotations = methodTarget.getMethod().getParameterAnnotations();
			if (parameterAnnotations.length == 0) {
				// No args
				return new ParseResult(methodTarget.getMethod(), methodTarget.getTarget(), null);
			}

			// Oh well, we need to convert some arguments
			final List<Object> arguments = new ArrayList<Object>(methodTarget.getMethod().getParameterTypes().length);

			// Attempt to parse
			Map<String, String> options = null;
			try {
				options = ParserUtils.tokenize(methodTarget.getRemainingBuffer());
			} catch (IllegalArgumentException e) {
				LOGGER.warning(ExceptionUtils.extractRootCause(e).getMessage());
				return null;
			}

			final Set<CliOption> cliOptions = getCliOptions(parameterAnnotations);
			for (CliOption cliOption : cliOptions) {
				Class<?> requiredType = methodTarget.getMethod().getParameterTypes()[arguments.size()];

				if (cliOption.systemProvided()) {
					Object result;
					if (SimpleParser.class.isAssignableFrom(requiredType)) {
						result = this;
					}
					else {
						LOGGER.warning("Parameter type '" + requiredType + "' is not system provided");
						return null;
					}
					arguments.add(result);
					continue;
				}

				// Obtain the value the user specified, taking care to ensure they only specified it via a single alias
				String value = null;
				String sourcedFrom = null;
				for (String possibleKey : cliOption.key()) {
					if (options.containsKey(possibleKey)) {
						if (sourcedFrom != null) {
							LOGGER.warning("You cannot specify option '" + possibleKey
									+ "' when you have also specified '" + sourcedFrom + "' in the same command");
							return null;
						}
						sourcedFrom = possibleKey;
						value = options.get(possibleKey);
					}
				}

				// Ensure the user specified a value if the value is mandatory or
				// key and value must appear in pair
				boolean mandatory = !StringUtils.hasText(value) && cliOption.mandatory();
				boolean specifiedKey = !StringUtils.hasText(value) && options.containsKey(sourcedFrom);
				boolean specifiedKeyWithoutValue = false;
				if(specifiedKey){
					value = cliOption.specifiedDefaultValue();
					if("__NULL__".equals(value)){
						specifiedKeyWithoutValue = true;
					}
				}
				if (mandatory || specifiedKeyWithoutValue) {
					if ("".equals(cliOption.key()[0])) {
						StringBuilder message = new StringBuilder("You should specify a default option ");
						if (cliOption.key().length > 1) {
							message.append("(otherwise known as option '").append(cliOption.key()[1]).append("') ");
						}
						message.append("for this command");
						LOGGER.warning(message.toString());
					}
					else {
						printHintMessage(cliOptions, options);
					}
					return null;
				}

				// Accept a default if the user specified the option, but didn't provide a value
				if ("".equals(value)) {
					value = cliOption.specifiedDefaultValue();
				}

				// Accept a default if the user didn't specify the option at all
				if (value == null) {
					value = cliOption.unspecifiedDefaultValue();
				}

				// Special token that denotes a null value is sought (useful for default values)
				if ("__NULL__".equals(value)) {
					if (requiredType.isPrimitive()) {
						LOGGER.warning("Nulls cannot be presented to primitive type " + requiredType.getSimpleName()
								+ " for option '" + StringUtils.arrayToCommaDelimitedString(cliOption.key()) + "'");
						return null;
					}
					arguments.add(null);
					continue;
				}

				// Now we're ready to perform a conversion
				try {
					CliOptionContext.setOptionContext(cliOption.optionContext());
					CliSimpleParserContext.setSimpleParserContext(this);
					Object result;
					Converter<?> c = null;
					for (Converter<?> candidate : converters) {
						if (candidate.supports(requiredType, cliOption.optionContext())) {
							// Found a usable converter
							c = candidate;
							break;
						}
					}
					if (c == null) {
						throw new IllegalStateException("TODO: Add basic type conversion");
						// TODO Fall back to a normal SimpleTypeConverter and attempt conversion
						// SimpleTypeConverter simpleTypeConverter = new SimpleTypeConverter();
						// result = simpleTypeConverter.convertIfNecessary(value, requiredType, mp);
					}

					// Use the converter
					result = c.convertFromText(value, requiredType, cliOption.optionContext());

					// If the option has been specified to be mandatory then the result should never be null
					if (result == null && cliOption.mandatory()) {
						throw new IllegalStateException();
					}
					arguments.add(result);
				} catch (RuntimeException e) {
					LOGGER.warning(e.getClass().getName() + ": Failed to convert '" + value + "' to type "
							+ requiredType.getSimpleName() + " for option '"
							+ StringUtils.arrayToCommaDelimitedString(cliOption.key()) + "'");
					if (StringUtils.hasText(e.getMessage())) {
						LOGGER.warning(e.getMessage());
					}
					return null;
				} finally {
					CliOptionContext.resetOptionContext();
					CliSimpleParserContext.resetSimpleParserContext();
				}
			}

			// Check for options specified by the user but are unavailable for the command
			Set<String> unavailableOptions = getSpecifiedUnavailableOptions(cliOptions, options);
			if (!unavailableOptions.isEmpty()) {
				StringBuilder message = new StringBuilder();
				if (unavailableOptions.size() == 1) {
					message.append("Option '").append(unavailableOptions.iterator().next()).append(
							"' is not available for this command. ");
				}
				else {
					message.append("Options ").append(
							StringUtils.collectionToDelimitedString(unavailableOptions, ", ", "'", "'")).append(
							" are not available for this command. ");
				}
				message.append("Use tab assist or the \"help\" command to see the legal options");
				LOGGER.warning(message.toString());
				return null;
			}

			return new ParseResult(methodTarget.getMethod(), methodTarget.getTarget(), arguments.toArray());
		}
	}

	/**
	 * @param cliOptions
	 * @param options
	 */
	private void printHintMessage(final Set<CliOption> cliOptions, Map<String, String> options) {
		boolean hintForOptions = true;

		StringBuilder optionBuilder = new StringBuilder();
		optionBuilder.append("You should specify option (");

		StringBuilder valueBuilder = new StringBuilder();
		valueBuilder.append("You should specify value for option '");

		List<List<String>> optionsKeys = getOptionsKeys(cliOptions,true);
		for (List<String> keys : optionsKeys) {
			boolean found = false;
			for (String key : keys) {
				if (options.containsKey(key)) {
					if (!StringUtils.hasText(options.get(key))) {
						valueBuilder.append(key);
						valueBuilder.append("' for this command");
						hintForOptions = false;
					}
					found = true;
					break;
				}
			}
			if (!found) {
				optionBuilder.append("--");
				optionBuilder.append(keys.get(0));
				optionBuilder.append(", ");
			}
		}
		//remove the ", " in the end.
		String hintForOption = optionBuilder.toString();
		hintForOption = hintForOption.substring(0, hintForOption.length() - 2);
		if (hintForOptions) {
			LOGGER.warning(hintForOption + ") for this command");
		}
		else {
			LOGGER.warning(valueBuilder.toString());
		}

	}

	/**
	 * Normalises the given raw user input string ready for parsing
	 *
	 * @param rawInput the string to normalise; can't be <code>null</code>
	 * @return a non-<code>null</code> string
	 */
	String normalise(final String rawInput) {
		// Replace all multiple spaces with a single space and then trim
		return rawInput.replaceAll(" +", " ").trim();
	}

	private Set<String> getSpecifiedUnavailableOptions(final Set<CliOption> cliOptions, final Map<String, String> options) {
		Set<String> cliOptionKeySet = new LinkedHashSet<String>();
		for (CliOption cliOption : cliOptions) {
			for (String key : cliOption.key()) {
				cliOptionKeySet.add(key.toLowerCase());
			}
		}
		Set<String> unavailableOptions = new LinkedHashSet<String>();
		for (String suppliedOption : options.keySet()) {
			if (!cliOptionKeySet.contains(suppliedOption.toLowerCase())) {
				unavailableOptions.add(suppliedOption);
			}
		}
		return unavailableOptions;
	}

	private Set<CliOption> getCliOptions(final Annotation[][] parameterAnnotations) {
		Set<CliOption> cliOptions = new LinkedHashSet<CliOption>();
		for (Annotation[] annotations : parameterAnnotations) {
			for (Annotation annotation : annotations) {
				if (annotation instanceof CliOption) {
					CliOption cliOption = (CliOption) annotation;
					cliOptions.add(cliOption);
				}
			}
		}
		return cliOptions;
	}

	protected void commandNotFound(final Logger logger, final String buffer) {
		logger.warning("Command '" + buffer + "' not found (for assistance press " + AbstractShell.completionKeys + ")");
	}

	private Collection<MethodTarget> locateTargets(final String buffer, final boolean strictMatching, final boolean checkAvailabilityIndicators) {
		Assert.notNull(buffer, "Buffer required");
		final Collection<MethodTarget> result = new HashSet<MethodTarget>();

		// The reflection could certainly be optimised, but it's good enough for now (and cached reflection
		// is unlikely to be noticeable to a human being using the CLI)
		for (final CommandMarker command : commands) {
			for (final Method method : command.getClass().getMethods()) {
				CliCommand cmd = method.getAnnotation(CliCommand.class);
				if (cmd != null) {
					// We have a @CliCommand.
					if (checkAvailabilityIndicators) {
						// Decide if this @CliCommand is available at this moment
						Boolean available = null;
						for (String value : cmd.value()) {
							MethodTarget mt = getAvailabilityIndicator(value);
							if (mt != null) {
								Assert.isNull(available, "More than one availability indicator is defined for '"
										+ method.toGenericString() + "'");
								try {
									available = (Boolean) mt.getMethod().invoke(mt.getTarget());
									// We should "break" here, but we loop over all to ensure no conflicting availability indicators are defined
								} catch (Exception e) {
									available = false;
								}
							}
						}
						// Skip this @CliCommand if it's not available
						if (available != null && !available) {
							continue;
						}
					}

					for (String value : cmd.value()) {
						String remainingBuffer = isMatch(buffer, value, strictMatching);
						if (remainingBuffer != null) {
							result.add(new MethodTarget(method, command, remainingBuffer, value));
						}
					}
				}
			}
		}
		return result;
	}

	static String isMatch(final String buffer, final String command, final boolean strictMatching) {
		if ("".equals(buffer.trim())) {
			return "";
		}
		String[] commandWords = StringUtils.delimitedListToStringArray(command, " ");
		int lastCommandWordUsed = 0;
		Assert.notEmpty(commandWords, "Command required");

		String bufferToReturn = null;
		String lastWord = null;

		next_buffer_loop: for (int bufferIndex = 0; bufferIndex < buffer.length(); bufferIndex++) {
			String bufferSoFarIncludingThis = buffer.substring(0, bufferIndex + 1);
			String bufferRemaining = buffer.substring(bufferIndex + 1);

			int bufferLastIndexOfWord = bufferSoFarIncludingThis.lastIndexOf(" ");
			String wordSoFarIncludingThis = bufferSoFarIncludingThis;
			if (bufferLastIndexOfWord != -1) {
				wordSoFarIncludingThis = bufferSoFarIncludingThis.substring(bufferLastIndexOfWord);
			}

			if (wordSoFarIncludingThis.equals(" ") || bufferIndex == buffer.length() - 1) {
				if (bufferIndex == buffer.length() - 1 && !"".equals(wordSoFarIncludingThis.trim())) {
					lastWord = wordSoFarIncludingThis.trim();
				}

				// At end of word or buffer. Let's see if a word matched or not
				for (int candidate = lastCommandWordUsed; candidate < commandWords.length; candidate++) {
					if (lastWord != null && lastWord.length() > 0 && commandWords[candidate].startsWith(lastWord)) {
						if (bufferToReturn == null) {
							// This is the first match, so ensure the intended match really represents the start of a command and not a later word within it
							if (lastCommandWordUsed == 0 && candidate > 0) {
								// This is not a valid match
								break next_buffer_loop;
							}
						}

						if (bufferToReturn != null) {
							// We already matched something earlier, so ensure we didn't skip any word
							if (candidate != lastCommandWordUsed + 1) {
								// User has skipped a word
								bufferToReturn = null;
								break next_buffer_loop;
							}
						}

						bufferToReturn = bufferRemaining;
						lastCommandWordUsed = candidate;
						if (candidate + 1 == commandWords.length) {
							// This was a match for the final word in the command, so abort
							break next_buffer_loop;
						}
						// There are more words left to potentially match, so continue
						continue next_buffer_loop;
					}
				}

				// This word is unrecognised as part of a command, so abort
				bufferToReturn = null;
				break next_buffer_loop;
			}

			lastWord = wordSoFarIncludingThis.trim();
		}

		// We only consider it a match if ALL words were actually used
		if (bufferToReturn != null) {
			if (!strictMatching || lastCommandWordUsed + 1 == commandWords.length) {
				return bufferToReturn;
			}
		}

		return null; // Not a match
	}

	public int complete(String buffer, int cursor, final List<String> candidates) {
		final List<Completion> completions = new ArrayList<Completion>();
		int result = completeAdvanced(buffer, cursor, completions);
		for (final Completion completion : completions) {
			candidates.add(completion.getValue());
		}
		return result;
	}

	public int completeAdvanced(String buffer, int cursor, final List<Completion> candidates) {
		synchronized (mutex) {
			Assert.notNull(buffer, "Buffer required");
			Assert.notNull(candidates, "Candidates list required");

			// Remove all spaces from beginning of command
			while (buffer.startsWith(" ")) {
				buffer = buffer.replaceFirst("^ ", "");
				cursor--;
			}

			// Replace all multiple spaces with a single space
			while (buffer.contains("  ")) {
				buffer = buffer.replaceFirst("  ", " ");
				cursor--;
			}

			// Begin by only including the portion of the buffer represented to the present cursor position
			String translated = buffer.substring(0, cursor);

			// Start by locating a method that matches
			final Collection<MethodTarget> targets = locateTargets(translated, false, true);
			SortedSet<Completion> results = new TreeSet<Completion>(COMPARATOR);

			if (targets.isEmpty()) {
				// Nothing matches the buffer they've presented
				return cursor;
			}
			if (targets.size() > 1) {
				// Assist them locate a particular target
				for (MethodTarget target : targets) {
					// Calculate the correct starting position
					int startAt = translated.length();

					// Only add the first word of each target
					int stopAt = target.getKey().indexOf(" ", startAt);
					if (stopAt == -1) {
						stopAt = target.getKey().length();
					}

					results.add(new Completion(target.getKey().substring(0, stopAt) + " "));
				}
				candidates.addAll(results);
				return 0;
			}

			// There is a single target of this method, so provide completion services for it
			MethodTarget methodTarget = targets.iterator().next();

			// Identify the command we're working with
			CliCommand cmd = methodTarget.getMethod().getAnnotation(CliCommand.class);
			Assert.notNull(cmd, "CliCommand unavailable for '" + methodTarget.getMethod().toGenericString() + "'");

			// Make a reasonable attempt at parsing the remainingBuffer
			Map<String, String> options;
			try {
				options = ParserUtils.tokenize(methodTarget.getRemainingBuffer());
			} catch (IllegalArgumentException ex) {
				// Assume any IllegalArgumentException is due to a quotation mark mismatch
				candidates.add(new Completion(translated + "\""));
				return 0;
			}

			// Lookup arguments for this target
			Annotation[][] parameterAnnotations = methodTarget.getMethod().getParameterAnnotations();

			// If there aren't any parameters for the method, at least ensure they have typed the command properly
			if (parameterAnnotations.length == 0) {
				for (String value : cmd.value()) {
					if (buffer.startsWith(value) || value.startsWith(buffer)) {
						results.add(new Completion(value)); // no space at the end, as there's no need to continue the command further
					}
				}
				candidates.addAll(results);
				return 0;
			}

			// If they haven't specified any parameters yet, at least verify the command name is fully completed
			if (options.isEmpty()) {
				for (String value : cmd.value()) {
					if (value.startsWith(buffer)) {
						// They are potentially trying to type this command
						// We only need provide completion, though, if they failed to specify it fully
						if (!buffer.startsWith(value)) {
							// They failed to specify the command fully
							results.add(new Completion(value + " "));
						}
					}
				}

				// Only quit right now if they have to finish specifying the command name
				if (results.size() > 0) {
					candidates.addAll(results);
					return 0;
				}
			}

			// To get this far, we know there are arguments required for this CliCommand, and they specified a valid command name

			// Record all the CliOptions applicable to this command
			List<CliOption> cliOptions = new ArrayList<CliOption>();
			for (Annotation[] annotations : parameterAnnotations) {
				CliOption cliOption = null;
				for (Annotation a : annotations) {
					if (a instanceof CliOption) {
						cliOption = (CliOption) a;
					}
				}
				Assert.notNull(cliOption, "CliOption not found for parameter '" + Arrays.toString(annotations) + "'");
				cliOptions.add(cliOption);
			}

			// Make a list of all CliOptions they've already included or are system-provided
			List<CliOption> alreadySpecified = new ArrayList<CliOption>();
			for (CliOption option : cliOptions) {
				for (String value : option.key()) {
					if (options.containsKey(value)) {
						alreadySpecified.add(option);
						break;
					}
				}
				if (option.systemProvided()) {
					alreadySpecified.add(option);
				}
			}

			// Make a list of all CliOptions they have not provided
			List<CliOption> unspecified = new ArrayList<CliOption>(cliOptions);
			unspecified.removeAll(alreadySpecified);

			// Determine whether they're presently editing an option key or an option value
			// (and if possible, the full or partial name of the said option key being edited)
			String lastOptionKey = null;
			String lastOptionValue = null;

			// The last item in the options map is *always* the option key they're editing (will never be null)
			if (options.size() > 0) {
				lastOptionKey = new ArrayList<String>(options.keySet()).get(options.keySet().size() - 1);
				lastOptionValue = options.get(lastOptionKey);
			}

			// Handle if they are trying to find out the available option keys; always present option keys in order
			// of their declaration on the method signature, thus we can stop when mandatory options are filled in
			if (methodTarget.getRemainingBuffer().endsWith("--")) {
				boolean showAllRemaining = true;
				for (CliOption include : unspecified) {
					if (include.mandatory()) {
						showAllRemaining = false;
						break;
					}
				}

				for (CliOption include : unspecified) {
					for (String value : include.key()) {
						if (!"".equals(value)) {
							results.add(new Completion(translated + value + " "));
						}
					}
					if (!showAllRemaining) {
						break;
					}
				}
				candidates.addAll(results);
				return 0;
			}

			// Handle suggesting an option key if they haven't got one presently specified (or they've completed a full option key/value pair)
			if (lastOptionKey == null
					|| (!"".equals(lastOptionKey) && !"".equals(lastOptionValue) && translated.endsWith(" "))) {
				// We have either NEVER specified an option key/value pair
				// OR we have specified a full option key/value pair

				// Let's list some other options the user might want to try (naturally skip the "" option, as that's the default)
				for (CliOption include : unspecified) {
					for (String value : include.key()) {
						// Manually determine if this non-mandatory but unspecifiedDefaultValue=* requiring option is able to be bound
						if (!include.mandatory() && "*".equals(include.unspecifiedDefaultValue()) && !"".equals(value)) {
							try {
								for (Converter<?> candidate : converters) {
									// Find the target parameter
									Class<?> paramType = null;
									int index = -1;
									for (Annotation[] a : methodTarget.getMethod().getParameterAnnotations()) {
										index++;
										for (Annotation an : a) {
											if (an instanceof CliOption) {
												if (an.equals(include)) {
													// Found the parameter, so store it
													paramType = methodTarget.getMethod().getParameterTypes()[index];
													break;
												}
											}
										}
									}
									if (paramType != null && candidate.supports(paramType, include.optionContext())) {
										// Try to invoke this usable converter
										candidate.convertFromText("*", paramType, include.optionContext());
										// If we got this far, the converter is happy with "*" so we need not bother the user with entering the data in themselves
										break;
									}
								}
							} catch (RuntimeException notYetReady) {
								if (translated.endsWith(" ")) {
									results.add(new Completion(translated + "--" + value + " "));
								}
								else {
									results.add(new Completion(translated + " --" + value + " "));
								}
								continue;
							}
						}

						// Handle normal mandatory options
						if (!"".equals(value) && include.mandatory()) {
							handleMandatoryCompletion(translated, unspecified, value, results);
						}
					}
				}

				// Only abort at this point if we have some suggestions; otherwise we might want to try to complete the "" option
				if (results.size() > 0) {
					candidates.addAll(results);
					return 0;
				}
			}

			// Handle completing the option key they're presently typing
			if ((lastOptionValue == null || "".equals(lastOptionValue)) && !translated.endsWith(" ")) {
				// Given we haven't got an option value of any form, and there's no space at the buffer end, we must still be typing an option key
				//System.out.println("completing an option");
				for (CliOption option : cliOptions) {
					for (String value : option.key()) {
						if (value != null && lastOptionKey != null
								&& value.regionMatches(true, 0, lastOptionKey, 0, lastOptionKey.length())) {
							String completionValue = translated.substring(0,
									(translated.length() - lastOptionKey.length()))
									+ value + " ";
							results.add(new Completion(completionValue));
						}
					}
				}
				candidates.addAll(results);
				return 0;
			}

			// To be here, we are NOT typing an option key (or we might be, and there are no further option keys left)
			if (lastOptionKey != null && !"".equals(lastOptionKey)&& (lastOptionValue.isEmpty() || buffer.endsWith(lastOptionValue))) {
				// Lookup the relevant CliOption that applies to this lastOptionKey
				// We do this via the parameter type
				Class<?>[] parameterTypes = methodTarget.getMethod().getParameterTypes();
				for (int i = 0; i < parameterTypes.length; i++) {
					CliOption option = cliOptions.get(i);
					Class<?> parameterType = parameterTypes[i];

					for (String key : option.key()) {
						if (key.equals(lastOptionKey)) {
							List<Completion> allValues = new ArrayList<Completion>();
							String suffix = " ";

							// Let's use a Converter if one is available
							for (Converter<?> candidate : converters) {
								if (candidate.supports(parameterType, option.optionContext())) {
									// Found a usable converter
									boolean addSpace = candidate.getAllPossibleValues(allValues, parameterType,
											lastOptionValue, option.optionContext(), methodTarget);
									if (!addSpace) {
										suffix = "";
									}
									break;
								}
							}

							if (allValues.isEmpty()) {
								// Doesn't appear to be a custom Converter, so let's go and provide defaults for simple types

								// Provide some simple options for common types
								if (Boolean.class.isAssignableFrom(parameterType)
										|| Boolean.TYPE.isAssignableFrom(parameterType)) {
									allValues.add(new Completion("true"));
									allValues.add(new Completion("false"));
								}

								if (Number.class.isAssignableFrom(parameterType)) {
									allValues.add(new Completion("0"));
									allValues.add(new Completion("1"));
									allValues.add(new Completion("2"));
									allValues.add(new Completion("3"));
									allValues.add(new Completion("4"));
									allValues.add(new Completion("5"));
									allValues.add(new Completion("6"));
									allValues.add(new Completion("7"));
									allValues.add(new Completion("8"));
									allValues.add(new Completion("9"));
								}
							}

							String prefix = "";
							if (!translated.endsWith(" ")) {
								prefix = " ";
							}

							// Only include in the candidates those results which are compatible with the present buffer
							for (Completion currentValue : allValues) {
								// We only provide a suggestion if the lastOptionValue == ""
								if (!StringUtils.hasText(lastOptionValue)) {
									// We should add the result, as they haven't typed anything yet
									results.add(new Completion(prefix + currentValue.getValue() + suffix,
											currentValue.getFormattedValue(), currentValue.getHeading(),
											currentValue.getOrder()));
								}
								else {
									// Only add the result **if** what they've typed is compatible *AND* they haven't already typed it in full
									if (currentValue.getValue().toLowerCase().startsWith(lastOptionValue.toLowerCase())
											&& !lastOptionValue.equalsIgnoreCase(currentValue.getValue())
											&& lastOptionValue.length() < currentValue.getValue().length()) {
										results.add(new Completion(prefix + currentValue.getValue() + suffix,
												currentValue.getFormattedValue(), currentValue.getHeading(),
												currentValue.getOrder()));
									}
								}
							}

							// ROO-389: give inline options given there's multiple choices available and we want to help the user
							StringBuilder help = new StringBuilder();
							help.append(OsUtils.LINE_SEPARATOR);
							help.append(option.mandatory() ? "required --" : "optional --");
							if ("".equals(option.help())) {
								help.append(lastOptionKey).append(": ").append("No help available");
							}
							else {
								help.append(lastOptionKey).append(": ").append(option.help());
							}
							if (option.specifiedDefaultValue().equals(option.unspecifiedDefaultValue())) {
								if (option.specifiedDefaultValue().equals("__NULL__")) {
									help.append("; no default value");
								}
								else {
									help.append("; default: '").append(option.specifiedDefaultValue()).append("'");
								}
							}
							else {
								if (!"".equals(option.specifiedDefaultValue())
										&& !"__NULL__".equals(option.specifiedDefaultValue())) {
									help.append("; default if option present: '").append(option.specifiedDefaultValue()).append(
											"'");
								}
								if (!"".equals(option.unspecifiedDefaultValue())
										&& !"__NULL__".equals(option.unspecifiedDefaultValue())) {
									help.append("; default if option not present: '").append(
											option.unspecifiedDefaultValue()).append("'");
								}
							}
							LOGGER.info(help.toString());

							if (results.size() == 1) {
								String suggestion = results.iterator().next().getValue().trim();
								if (suggestion.equals(lastOptionValue)) {
									// They have pressed TAB in the default value, and the default value has already been provided as an explicit option
									return 0;
								}
							}

							if (results.size() > 0) {
								candidates.addAll(results);
								// Values presented from the last space onwards
								if (translated.endsWith(" ")) {
									return translated.lastIndexOf(" ") + 1;
								}
								return translated.trim().lastIndexOf(" ");
							}
							return 0;
						}
					}
				}
			}
            //todo edit by linux_china
            // has CliOption with empty key
            boolean hasEmptyKey = false;
            CliOption option = null;
            Class<?> parameterType = null;
            Class<?>[] parameterTypes = methodTarget.getMethod().getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                CliOption cliOption = cliOptions.get(i);
                if (cliOption.key().length == 1 && cliOption.key()[0].isEmpty()) {
                    hasEmptyKey = true;
                    option = cliOption;
                    parameterType = parameterTypes[i];
                    break;
                }

            }
            if (hasEmptyKey) {
                if (buffer.endsWith(" ")) {
                    lastOptionValue = "";
                }
                List<Completion> allValues = new ArrayList<Completion>();
                String suffix = " ";

                // Let's use a Converter if one is available
                for (Converter<?> candidate : converters) {
                    if (candidate.supports(parameterType, option.optionContext())) {
                        // Found a usable converter
                        boolean addSpace = candidate.getAllPossibleValues(allValues, parameterType,
                                "", option.optionContext(), methodTarget);
                        if (!addSpace) {
                            suffix = "";
                        }
                        break;
                    }
                }

                if (allValues.isEmpty()) {
                    // Doesn't appear to be a custom Converter, so let's go and provide defaults for simple types

                    // Provide some simple options for common types
                    if (Boolean.class.isAssignableFrom(parameterType)
                            || Boolean.TYPE.isAssignableFrom(parameterType)) {
                        allValues.add(new Completion("true"));
                        allValues.add(new Completion("false"));
                    }

                    if (Number.class.isAssignableFrom(parameterType)) {
                        allValues.add(new Completion("0"));
                        allValues.add(new Completion("1"));
                        allValues.add(new Completion("2"));
                        allValues.add(new Completion("3"));
                        allValues.add(new Completion("4"));
                        allValues.add(new Completion("5"));
                        allValues.add(new Completion("6"));
                        allValues.add(new Completion("7"));
                        allValues.add(new Completion("8"));
                        allValues.add(new Completion("9"));
                    }
                }
                String prefix = " ";
                // Only include in the candidates those results which are compatible with the present buffer
                for (Completion currentValue : allValues) {
                    // We only provide a suggestion if the lastOptionValue == ""
                    if (lastOptionValue==null || lastOptionValue.isEmpty()) {
                        // We should add the result, as they haven't typed anything yet
                        results.add(new Completion(prefix + currentValue.getValue() + suffix,
                                currentValue.getFormattedValue(), currentValue.getHeading(),
                                currentValue.getOrder()));
                    } else {
                        // Only add the result **if** what they've typed is compatible *AND* they haven't already typed it in full
                        if (currentValue.getValue().toLowerCase().startsWith(lastOptionValue.toLowerCase())
                                && !lastOptionValue.equalsIgnoreCase(currentValue.getValue())
                                && lastOptionValue.length() < currentValue.getValue().length()) {
                            results.add(new Completion(prefix + currentValue.getValue() + suffix,
                                    currentValue.getFormattedValue(), currentValue.getHeading(),
                                    currentValue.getOrder()));
                        }
                    }
                }
                if(results.isEmpty()) {
                    // ROO-389: give inline options given there's multiple choices available and we want to help the user
                    StringBuilder help = new StringBuilder();
                    help.append(OsUtils.LINE_SEPARATOR);
                    help.append(option.mandatory() ? "required --" : "optional --");
                    if ("".equals(option.help())) {
                        help.append(lastOptionKey).append(": ").append("No help available");
                    } else {
                        help.append(lastOptionKey).append(": ").append(option.help());
                    }
                    if (option.specifiedDefaultValue().equals(option.unspecifiedDefaultValue())) {
                        if (option.specifiedDefaultValue().equals("__NULL__")) {
                            help.append("; no default value");
                        } else {
                            help.append("; default: '").append(option.specifiedDefaultValue()).append("'");
                        }
                    } else {
                        if (!"".equals(option.specifiedDefaultValue())
                                && !"__NULL__".equals(option.specifiedDefaultValue())) {
                            help.append("; default if option present: '").append(option.specifiedDefaultValue()).append(
                                    "'");
                        }
                        if (!"".equals(option.unspecifiedDefaultValue())
                                && !"__NULL__".equals(option.unspecifiedDefaultValue())) {
                            help.append("; default if option not present: '").append(
                                    option.unspecifiedDefaultValue()).append("'");
                        }
                    }
                    LOGGER.info(help.toString());
                }
                if (results.size() == 1) {
                    String suggestion = results.iterator().next().getValue().trim();
                    if (suggestion.equals(lastOptionValue)) {
                        // They have pressed TAB in the default value, and the default value has already been provided as an explicit option
                        return 0;
                    }
                }

                if (results.size() > 0) {
                    candidates.addAll(results);
                    // Values presented from the last space onwards
                    if (translated.endsWith(" ")) {
                        return translated.lastIndexOf(" ") + 1;
                    }
                    return translated.trim().lastIndexOf(" ");
                }
                return 0;
            }
            //todo end by linux_china
            return 0;
		}
	}

	/**
	 * populate completion for mandatory options
	 *
	 * @param translated user's input
	 * @param unspecified unspecified options
	 * @param value the option key
	 * @param results completion list
	 */
	private void handleMandatoryCompletion(String translated, List<CliOption> unspecified, String value, SortedSet<Completion> results) {
		StringBuilder strBuilder = new StringBuilder(translated);
		if (!translated.endsWith(" ")) {
			strBuilder.append(" ");
		}
		// Plan change for SHL-20. But usability is bad.
		/*
		List<List<String>> mandatoryOptions = getMandatoryOptions(unspecified);
		for (List<String> option : mandatoryOptions) {
			strBuilder.append("--");
			strBuilder.append(option.get(0));
			strBuilder.append(" ");
		}
		*/
		strBuilder.append("--");
		strBuilder.append(value);
		strBuilder.append(" ");
		results.add(new Completion(strBuilder.toString()));
	}


	public void obtainHelp(@CliOption(key = { "", "command" }, optionContext = "availableCommands", help = "Command name to provide help for") String buffer) {
		synchronized (mutex) {
			if (buffer == null) {
				buffer = "";
			}

			StringBuilder sb = new StringBuilder();

			// Figure out if there's a single command we can offer help for
			final Collection<MethodTarget> matchingTargets = locateTargets(buffer, false, false);
			if (matchingTargets.size() == 1) {
				// Single command help
				MethodTarget methodTarget = matchingTargets.iterator().next();

				// Argument conversion time
				Annotation[][] parameterAnnotations = methodTarget.getMethod().getParameterAnnotations();
				if (parameterAnnotations.length > 0) {
					// Offer specified help
					CliCommand cmd = methodTarget.getMethod().getAnnotation(CliCommand.class);
					Assert.notNull(cmd, "CliCommand not found");

					for (String value : cmd.value()) {
						sb.append("Keyword:                   ").append(value).append(OsUtils.LINE_SEPARATOR);
					}

					sb.append("Description:               ").append(cmd.help()).append(OsUtils.LINE_SEPARATOR);

					for (Annotation[] annotations : parameterAnnotations) {
						CliOption cliOption = null;
						for (Annotation a : annotations) {
							if (a instanceof CliOption) {
								cliOption = (CliOption) a;

								for (String key : cliOption.key()) {
									if ("".equals(key)) {
										key = "** default **";
									}
									sb.append(" Keyword:                  ").append(key).append(
											OsUtils.LINE_SEPARATOR);
								}

								sb.append("   Help:                   ").append(cliOption.help()).append(
										OsUtils.LINE_SEPARATOR);
								sb.append("   Mandatory:              ").append(cliOption.mandatory()).append(
										OsUtils.LINE_SEPARATOR);
								sb.append("   Default if specified:   '").append(cliOption.specifiedDefaultValue()).append(
										"'").append(OsUtils.LINE_SEPARATOR);
								sb.append("   Default if unspecified: '").append(cliOption.unspecifiedDefaultValue()).append(
										"'").append(OsUtils.LINE_SEPARATOR);
								sb.append(OsUtils.LINE_SEPARATOR);
							}

						}
						Assert.notNull(cliOption, "CliOption not found for parameter '" + Arrays.toString(annotations)
								+ "'");
					}
				}
				// Only a single argument, so default to the normal help operation
			}

			SortedSet<String> result = new TreeSet<String>(COMPARATOR);
			for (MethodTarget mt : matchingTargets) {
				CliCommand cmd = mt.getMethod().getAnnotation(CliCommand.class);
				if (cmd != null) {
					for (String value : cmd.value()) {
						if ("".equals(cmd.help())) {
							result.add("* " + value);
						}
						else {
							result.add("* " + value + " - " + cmd.help());
						}
					}
				}
			}

			for (String s : result) {
				sb.append(s).append(OsUtils.LINE_SEPARATOR);
			}

			LOGGER.info(sb.toString());
//			LOGGER.warning("** Type 'hint' (without the quotes) and hit ENTER for step-by-step guidance **"
//					+ StringUtils.LINE_SEPARATOR);
		}
	}

	public Set<String> getEveryCommand() {
		synchronized (mutex) {
			SortedSet<String> result = new TreeSet<String>(COMPARATOR);
			for (Object o : commands) {
				Method[] methods = o.getClass().getMethods();
				for (Method m : methods) {
					CliCommand cmd = m.getAnnotation(CliCommand.class);
					if (cmd != null) {
						result.addAll(Arrays.asList(cmd.value()));
					}
				}
			}
			return result;
		}
	}

	public final void add(final CommandMarker command) {
		synchronized (mutex) {
			commands.add(command);
			for (final Method method : command.getClass().getMethods()) {
				CliAvailabilityIndicator availability = method.getAnnotation(CliAvailabilityIndicator.class);
				if (availability != null) {
					Assert.isTrue(
							method.getParameterTypes().length == 0,
							"CliAvailabilityIndicator is only legal for 0 parameter methods ("
									+ method.toGenericString() + ")");
					Assert.isTrue(
							method.getReturnType().equals(Boolean.TYPE),
							"CliAvailabilityIndicator is only legal for primitive boolean return types ("
									+ method.toGenericString() + ")");
					for (String cmd : availability.value()) {
						Assert.isTrue(!availabilityIndicators.containsKey(cmd),
								"Cannot specify an availability indicator for '" + cmd + "' more than once");
						availabilityIndicators.put(cmd, new MethodTarget(method, command));
					}
				}
			}
		}
	}

	public final void remove(final CommandMarker command) {
		synchronized (mutex) {
			commands.remove(command);
			for (Method m : command.getClass().getMethods()) {
				CliAvailabilityIndicator availability = m.getAnnotation(CliAvailabilityIndicator.class);
				if (availability != null) {
					for (String cmd : availability.value()) {
						availabilityIndicators.remove(cmd);
					}
				}
			}
		}
	}

	public final void add(final Converter<?> converter) {
		synchronized (mutex) {
			converters.add(converter);
		}
	}

	public final void remove(final Converter<?> converter) {
		synchronized (mutex) {
			converters.remove(converter);
		}
	}
}
