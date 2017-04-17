<?php

/**
 * A container class for holding and transforming various plugin metadata.
 *
 * @author faishal
 */
class rtPluginInfo {

    //Most fields map directly to the contents of the plugin's info.json file.
    //See the relevant docs for a description of their meaning.
    public $name;
    public $slug;
    public $version;
    public $homepage;
    public $sections;
    public $download_url;
    public $author;
    public $author_homepage;
    public $requires;
    public $tested;
    public $upgrade_notice;
    public $rating;
    public $num_ratings;
    public $downloaded;
    public $last_updated;
    public $id = 0; //The native WP.org API returns numeric plugin IDs, but they're not used for anything.

    /**
     * Create a new instance of PluginInfo from JSON-encoded plugin info
     * returned by an external update API.
     *
     * @param string $json Valid JSON string representing plugin info.
     * @param bool $triggerErrors
     * @return PluginInfo|null New instance of PluginInfo, or NULL on error.
     */

    /**
     *
     * @param type $json
     * @param type $triggerErrors
     * @return null|\self
     */
    public static function fromJson($json, $triggerErrors = false) {
        /** @var StdClass $apiResponse */
        $apiResponse = json_decode($json);
        if (empty($apiResponse) || !is_object($apiResponse)) {
            if ($triggerErrors) {
                trigger_error(
                        sprintf( __( "Failed to parse plugin metadata. Try validating your .json file with %s", 'rtmedia' ), 'http://jsonlint.com/' ), E_USER_NOTICE
                );
            }
            return null;
        }

        //Very, very basic validation.
        $valid = isset($apiResponse->name) && !empty($apiResponse->name) && isset($apiResponse->version) && !empty($apiResponse->version);
        if (!$valid) {
            if ($triggerErrors) {
                trigger_error(
                        __( "The plugin metadata file does not contain the required 'name' and/or 'version' keys.", 'rtmedia'), E_USER_NOTICE
                );
            }
            return null;
        }

        $info = new self();
        foreach (get_object_vars($apiResponse) as $key => $value) {
            $info->$key = $value;
        }

        return $info;
    }

    /**
     * Transform plugin info into the format used by the native WordPress.org API
     *
     * @return object
     */

    /**
     *
     * @return \StdClass
     */
    public function toWpFormat() {
        $info = new StdClass;

        //The custom update API is built so that many fields have the same name and format
        //as those returned by the native WordPress.org API. These can be assigned directly.
        $sameFormat = array(
            'name', 'slug', 'version', 'requires', 'tested', 'rating', 'upgrade_notice',
            'num_ratings', 'downloaded', 'homepage', 'last_updated',
        );
        foreach ($sameFormat as $field) {
            if (isset($this->$field)) {
                $info->$field = $this->$field;
            } else {
                $info->$field = null;
            }
        }

        //Other fields need to be renamed and/or transformed.
        $info->download_link = $this->download_url;

        if (!empty($this->author_homepage)) {
            $info->author = sprintf('<a href="%s">%s</a>', $this->author_homepage, $this->author);
        } else {
            $info->author = $this->author;
        }

        if (is_object($this->sections)) {
            $info->sections = get_object_vars($this->sections);
        } elseif (is_array($this->sections)) {
            $info->sections = $this->sections;
        } else {
            $info->sections = array('description' => '');
        }

        return $info;
    }

}
