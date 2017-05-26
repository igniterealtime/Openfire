<?php 
/**
 * Update script for cc1 => cc2 2.0r1
 * 
 * @author Konrad Sroka
 * @author Fabian Wolf
 * @package cc2
 * 
 */

// include required classes and functions
require_once( get_template_directory() . '/includes/admin/updates/update-helpers.php' );
//require_once( get_template_directory() . '/includes/admin/updates/cc20-update-slideshow.php' );


// actual class

class updateToCC_TwoZero extends updateHelper {
	var $themeVersion = '2.0',
		$updatePrefix = 'update_cc1_to_cc20_';
	
	function __construct() {
		// add filter actions
		
	}
	
	public function convert_font_family( $old_font_data ) {
		$return = $old_font_data;
		// find kgv
		
		
		return $return;
	}

	/**
	 * Structure of update data:
	 *
	 * array(
	 *    'section_name' => array(
	 * 'old_option_name' => 'custom_community_theme_options',
	 * 'old_setting_prefix' => 'cap_',
	 * 'new_option_name' =>'cc2_advanded_settings',
	 * 'import_data' => array(
	 * 'old_option' => 'new_option',
	 * 'old_option_2' => => array(
	 * 'new_setting_name' => 'new_option_2',
	 * 'value_conversion' => array(
	 * 'on' => true,
	 * 'off' => false,
	 * ),
	 * ),
	 * 'old_option_3' => array(
	 * 'new_setting_name' => 'new_option_3',
	 * 'callback' => 'font_conversion', // a method or function callback; use array( 'className', 'callback_method') if you want to use an external class instead the current one
	 * ),
	 * )
	 * ); // end of section $section_name
	 * @param bool $arrSelectedSections
	 * @return array
	 */
	
	function run_updates( $arrSelectedSections = false ) {
		$return = array(
			'type' => 'error',
			'message' => 'Something went TOTALLY wrong.',
		);
		
		if( !empty( $this->arrImportOptions ) ) {
			
			
			// prepare section selections
			if( empty( $arrSelectedSections ) ) { // no sections selected? use ALL sections
				$arrSelectedSections = array_keys( $this->arrImportOptions ); 
				
				parent::debug( $arrSelectedSections, '$arrSelectedSections' );
			}
			
			// context: import options
			foreach( $this->arrImportOptions as $strSection => $arrSectionOptionData ) {
				// soft resets
				$strOldSettingsPrefix = '';
				$arrImportedOptions = array(); // we specifically use a different name for the variable that will store the updated data
				
				// test if section is requested for import, and if so, whether there are actual import data or if it's just a stub
		
				
				parent::debug( $arrOptionData, $strSection . ':arrOptionData' );
				
				// context: section
				if( !empty( $arrSectionOptionData['import_data'] ) && is_array( $arrSectionOptionData['import_data'] ) ) { // section contains import data
					parent::debug( $strSection . ' fires' );
					
					$arrImportData = $arrSectionOptionData['import_data'];
					
					parent::debug( $arrImportData, 'arrImportData for [' . $strSection . ']' );
					
					// fetch old data first
					$arrOldOptionsData = get_option( $arrSectionOptionData['old_option_name'], false );
					
					parent::debug( $arrOldOptionsData, 'arrOldOptionsData for [' . $strSection . ']' );
					
					// is there a global prefix defined for the old options?
					if( isset( $arrSectionOptionData['old_setting_prefix'] ) ) {
						$strOldSettingsPrefix = $arrSectionOptionData['old_setting_prefix'];
					}
					
					// found old data
					if( !empty( $arrOldOptionsData ) ) {
						
						
						// cycle through the options of the current section
						foreach( $arrImportData as $strOldOption => $newOption ) {
							
							// preparation for the sanity check
							$strNewOptionName = ( !isset( $newOption['new_setting_name'] ) ? $newOption : $newOption['new_setting_name'] );
							
							if( !is_array( $newOption ) ) { // withour further addo ..
								
								//$arrImportedOptions[ $newOption ] = $arrOldOptionsData[ $strOldSettingsPrefix . $strOldOption ];
								$arrImportedOptions[ $strNewOptionName ] = $arrOldOptionsData[ $strOldSettingsPrefix . $strOldOption ];
								
								
								
							} else { // going deeper (has some parameters)
								
								if( isset( $newOption['value_conversion'] ) ) { // old values may not be the same as before, eg. for true/false or selections
									$arrImportedOptions[ $newOption['new_setting_name'] ] = parent::convert_value_choice( $arrOldOptionsData[ $strOldSettingsPrefix . $strOldOption], $newOption['value_conversion'] );
								}
								
								// not so much a callback than a filter
								
								if( isset( $newOption['callback'] ) ) { // handled by the helper class
									switch( $newOption['callback'] ) {
										
										case 'convert_font_family':
											$arrImportedOptions[ $newOption['new_setting_name'] ] = $this->filter_convert_font( $arrOldOptionsData[ $strOldSettingsPrefix . $strOldOption] );
											break;
									
									
										// unknown method or separate function / method of another class
										default:
											$arrImportedOptions[ $newOption['new_setting_name'] ] = parent::handle_callback( $value, $this->updatePrefix . $newOption['callback'] );
											break;
									}
								}
							}
							
							// optional sanety check: discard import if empty
							if( isset( $arrSectionOptionData['allow_empty_values']) && $arrSectionOptionData['allow_empty_values'] == false ) {
								if( trim($arrOldOptionsData[ $strOldSettingsPrefix . $strOldOption ]) == '' || empty( $arrOldOptionsData[ $strOldSettingsPrefix . $strOldOption ] ) ) {
									unset( $arrImportedOptions[ $strNewOptionName ] );
								}
							}
							
						}
						
						// finally, update options
							
						// test run? => test with transient
						if( $this->is_test_run != false ) {	
							// delete already used transient first
							
							delete_transient( $this->classPrefix . 'test_run_' . $arrSectionOptionData['new_option_name'] );
							
							// set the transient anew
							set_transient( $this->classPrefix . 'test_run_' . $arrSectionOptionData['new_option_name'], $arrImportedOptions, 15 * 60 ); // times out after 10 minutes
							
							// check if transient was actually set
							
							$testRunResult = get_transient( $this->classPrefix . 'test_run_' . $arrSectionOptionData['new_option_name'], false );
							if( $testRunResult == false || $testRunResult != $arrImportedOptions ) {
								$arrReturn[ $strSection ] = array(
									'type' => 'error test-run',
									'message' => 'Error: Test run failed; could not add the updated data to the current theme options.',
								);
							} else {
								$arrReturn[ $strSection ] = array(
									'type' => 'success',
									'message' => 'Successfully completed the test run for [' . $strSection . ']; all lights green! ;-)',
								);
							}
							
						} else { // go for the real thing
							// fetch current option data
							$currentData = get_option( $arrOptionData[ $strSection ]['new_option_name'], false );
							$currentDataBackup = $currentData;
							
							if( !empty( $currentData ) ) {
								
								// cycle through the imported data
								foreach( $arrImportedOptions as $strSetting => $data ) {
									
									
									// overwrite existing data - but ONLY if the IMPORTED data is NOT empty
									$currentData[ $strSetting ] = $data;
									
								}
							} else {
								$currentData = $arrImportedOptions;
							}
							
							// inital test with different option name
							parent::debug( $currentData, 'currentData for [' . $strSection . ']' );
							
							
							
							//$strUpdateOptionName = 'cc20_update_'  . $arrSectionOptionData['new_option_name'];
							
							// weaponized ..
							$strUpdateOptionName = $arrSectionOptionData['new_option_name'];
							
							
							
							
							/** 
							 * NOTE: for testing purposes!
							 * update will fail if there is no ACTUAL update happening
							 */
							
							if( $currentData != $currentDataBackup && !empty( $currentData) ) {
								$currentData['cc2_data_imported'] = time();
							
								$updateResult = update_option( $strUpdateOptionName, $currentData );
							
							
								//$updateResult = update_option( $arrOptionData[ $strSection ]['new_option_name'], $currentData );
								
								if( $updateResult != false ) {
									$arrReturn[ $strSection ] = array(
										'type' => 'success',
										'message' => 'Successfully imported old data (option: ['.$strUpdateOptionName.']).',
										'section' => $strSection,
									);
								} else {
									$arrReturn[ $strSection ] = array(
										'type' => 'error import',
										'message' => 'Error: Could not import old data (failed updating the option ['.$strUpdateOptionName.'] )',
										'section' => $strSection,
									);
								}
							} else {
								$arrReturn[ $strSection ] = array(
									'type' => 'error import no-updated-data',
									'message' => 'Error: Could not import old data, because all available data is ALREADY imported!',
									'section' => $strSection,
								);
							}
							
							
						}
						
					} else { // no old data found
						$arrReturn[ $strSection ] = array(
							'type' => 'error missing-option',
							'message' => sprintf('Import for %s failed, because the related option (%s) is empty or does not exist', $strSection, $arrOptionData[ $strSection ]['old_option_name'] ),
						);
					}
				}
			}
			
		} else {
			parent::debug( 'arrImportOptions are empty!' );
		}
		
		if( !empty( $arrReturn ) ) {
			$return = $arrReturn;
		}
		
		return $return;
	}
	
	
}


// return 
if( updateHelper::is_correct_version( '2.0', '2.1') == false ) {
	$arrResult = array(
		'type' => 'error wrong-version',
		'message' => 'Wrong update script version.',
	);
	
	//echo 'does not compute';
	
	//break;
	return;
}

// include update data
require_once( get_template_directory() . '/includes/admin/updates/cc20-update-data.php');

// test if there actually ARE old settings
$has_old_settings = updateHelper::has_importable_settings( $arrOptions );

if( $has_old_settings == false ) {
	$arrResult = array(
		'type' => 'error no-old-settings',
		'message' => 'No old settings for import found.',
	);
	$exec_update = false;
}

// abort if specific $run_update variable is not set
if( !isset( $exec_update ) || $exec_update != true ) {
	//echo 'exec_update is false.';
	//break;
	return;
}


// init update class
$update = new updateToCC_TwoZero();

// load update data (default)
$arrUpdateData['import_options'] = $arrOptions;

// check if all or just specific settings
if( !empty( $arrCustomUpdateSections ) && is_array( $arrCustomUpdateSections) ) {
	
	updateHelper::debug( array( 'chosen sections' => $arrCustomUpdateSections, 'available sections' => array_keys( $arrOptions ) ) );
	
	foreach( array_keys( $arrOptions ) as $strSectionKey ) {
		if( !in_array( $strSectionKey, $arrCustomUpdateSections ) ) {
			unset( $arrUpdateData['import_options'][ $strSectionKey ] );
		}
	}
		
	unset( $strSectionKey );
}


/**
 * FIXME: Move all of this $_POST form stuff handling to the actual admin section, so that this script could properly be run headless / via cron.
 */

if( isset( $is_test_run) ) {
	// simplified calls for better readability
	$arrUpdateData['test_run'] = false; // default setting
	
	if( $is_test_run == true ) {
		$arrUpdateData['test_run'] = true;
	}
}

/*if( !empty( $_POST['update_test_run'] ) ) {
	// simplified calls for better readability
	$arrUpdateData['test_run'] = false; // default setting
	
	if( $_POST['update_test_run'] == 'yes' ) {
		$arrUpdateData['test_run'] = true;
	}
}*/

$update->load_settings( $arrUpdateData );




$arrResult = $update->run_updates();