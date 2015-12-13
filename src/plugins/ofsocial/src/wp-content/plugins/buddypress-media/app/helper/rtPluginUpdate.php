<?php

/**
 * Description of rtPluginUpdate
 * A simple container class for holding information about an available update.
 * @author faishal
 */
class rtPluginUpdate {

    public $id = 0;
    public $slug;
    public $version;
    public $homepage;
    public $download_url;
    public $upgrade_notice;
    private static $fields = array('id', 'slug', 'version', 'homepage', 'download_url', 'upgrade_notice');

    /**
     * Create a new instance of PluginUpdate from its JSON-encoded representation.
     *
     * @param string $json
     * @param bool $triggerErrors
     * @return PluginUpdate|null
     */

    /**
     *
     * @param type $json
     * @param type $triggerErrors
     * @return null
     */
    public static function fromJson($json, $triggerErrors = false) {
        //Since update-related information is simply a subset of the full plugin info,
        //we can parse the update JSON as if it was a plugin info string, then copy over
        //the parts that we care about.
        $pluginInfo = rtPluginInfo::fromJson($json, $triggerErrors);
        if ($pluginInfo != null) {
            return self::fromPluginInfo($pluginInfo);
        } else {
            return null;
        }
    }

    /**
     * Create a new instance of PluginUpdate based on an instance of PluginInfo.
     * Basically, this just copies a subset of fields from one object to another.
     *
     * @param PluginInfo $info
     * @return PluginUpdate
     */

    /**
     *
     * @param type $info
     * @return type
     */
    public static function fromPluginInfo($info) {
        return self::fromObject($info);
    }

    /**
     * Create a new instance of PluginUpdate by copying the necessary fields from
     * another object.
     *
     * @param StdClass|PluginInfo|PluginUpdate $object The source object.
     * @return PluginUpdate The new copy.
     */

    /**
     *
     * @param type $object
     * @return \self
     */
    public static function fromObject($object) {
        $update = new self();
        foreach (self::$fields as $field) {
            $update->$field = $object->$field;
        }
        return $update;
    }

    /**
     * Create an instance of StdClass that can later be converted back to
     * a PluginUpdate. Useful for serialization and caching, as it avoids
     * the "incomplete object" problem if the cached value is loaded before
     * this class.
     *
     * @return StdClass
     */

    /**
     *
     * @return \StdClass
     */
    public function toStdClass() {
        $object = new StdClass();
        foreach (self::$fields as $field) {
            $object->$field = $this->$field;
        }
        return $object;
    }

    /**
     * Transform the update into the format used by WordPress native plugin API.
     *
     * @return object
     */

    /**
     *
     * @return \StdClass
     */
    public function toWpFormat() {
        $update = new StdClass;

        $update->id = $this->id;
        $update->slug = $this->slug;
        $update->new_version = $this->version;
        $update->url = $this->homepage;
        $update->package = $this->download_url;
        if (!empty($this->upgrade_notice)) {
            $update->upgrade_notice = $this->upgrade_notice;
        }

        return $update;
    }

}
