<?php

//   Extracted from CodingTeam for the Jappix project.

//   This file is a part of CodingTeam. Take a look at <http://codingteam.org>.
//   Copyright © 2007-2010 Erwan Briand <erwan@codingteam.net>
//
//   This program is free software: you can redistribute it and/or modify it
//   under the terms of the GNU Affero General Public License as published by
//   the Free Software Foundation, version 3 only.
//
//   This program is distributed in the hope that it will be useful, but
//   WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
//   or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
//   License for more details.
//
//   You should have received a copy of the GNU Affero General Public License
//   along with this program. If not, see <http://www.gnu.org/licenses/>.

/**
 * @file
 * This file contains the DrawSVGChart class.
 */

/**
 * DrawSVGChart class
 */
class DrawSVGChart {
    private $datas, $legend, $link, $xml_object, $svg,
            $xml_elements, $evolution;
    public $has_errors;

    function createChart($datas=array(), $legend=array(), $link,
                         $evolution=FALSE, $type='others')
    {
        $this->has_errors = FALSE;
        $max = 0;

        // One or two data arrays
        if (isset($datas[0]) && is_array($datas[0]))
        {
            $datas_number = count($datas[0]);

            if ($datas_number >= 1)
                $max = max($datas[0]);
            else
                $this->has_errors = TRUE;
        }
        else
        {
            $datas_number = count($datas);

            if ($datas_number >= 1)
                $max = max($datas);
            else
                $this->has_errors = TRUE;
        }

        // Set the width of the chart
        if ($datas_number * 55 > 400)
            $width = $datas_number * 55;
        else
            $width = 400;

        $height = 250;
        $this->datas = $datas;
        $this->legend = $legend;
        $this->link = $link;
        $this->evolution = $evolution;
        $this->type = $type;
        $this->xml_elements = array();

        // Scale
        if ($max <= 20)
        {
            $scale[4] = 20;
            $scale[3] = 15;
            $scale[2] = 10;
            $scale[1] = 5;
        }
        else
        {
            $scale[4] = ceil($max / 20) * 20;
            $scale[3] = $scale[4] * 3/4;
            $scale[2] = $scale[4] * 2/4;
            $scale[1] = $scale[4] * 1/4;
        }

        if ($scale[4] == 0 || $max == 0)
            $this->has_errors = TRUE;

        if ($this->has_errors)
            return TRUE;

        $this->xml_object = new DOMDocument('1.0', 'utf-8');
	
	// Process the static file host prefix
	$static_prefix = '.';
	
	if(hasStatic())
		$static_prefix = HOST_STATIC.'/php';
	
        // Add the stylesheet
        $style = $this->xml_object->createProcessingInstruction("xml-stylesheet",
                 "type='text/css' href='".getFiles(genHash(getVersion()), '', 'css', '', 'stats-svg.css')."'");
        $this->xml_object->appendChild($style);

        // Create the root SVG element
        $this->svg = $this->xml_object->createElement('svg');
        $this->svg->setAttribute('xmlns:svg', 'http://www.w3.org/2000/svg');
        $this->svg->setAttribute('xmlns', 'http://www.w3.org/2000/svg');
        $this->svg->setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink');
        $this->svg->setAttribute('version', '1.1');
        $this->svg->setAttribute('width', $width);
        $this->svg->setAttribute('height', $height);
        $this->svg->setAttribute('id', 'svg');
        $this->xml_object->appendChild($this->svg);

        // Create a definition
        $this->xml_elements['basic_defs'] = $this->xml_object->createElement('defs');
        $path = $this->xml_object->createElement('path');
        $path->setAttribute('id', 'mark');
        $path->setAttribute('d', 'M 0,234 v 4 ');
        $path->setAttribute('stroke', '#596171');
        $path->setAttribute('stroke-width', '2px');
        $this->xml_elements['basic_defs']->appendChild($path);

        // Create the static background
        $this->xml_elements['static_background'] = $this->xml_object->createElement('g');
        $this->xml_elements['static_background']->setAttribute('class', 'static-background');

        // Draw the legend
        $this->drawLegend();

        // Draw the table
        $this->drawTable($scale, $width);

        // Draw the chart
        $this->drawChart($scale, $width);
    }

    function drawLegend()
    {
        $pstart = 3;
        $tstart = 7;

        foreach ($this->legend as $item)
        {
            $val_path = $pstart + 11;
            $val_text = $tstart + 10;

            // Create the legend line
            $path = $this->xml_object->createElement('path');
            $path->setAttribute('d', 'M 40, '.$val_path.' L 55, '.$val_path);
            $path->setAttribute('id', 'legendline');
            $path->setAttribute('stroke', $item[0]);
            $path->setAttribute('stroke-width', '2px');

            // Create the legend text
            $text = $this->xml_object->createElement('text', $item[1]);
            $text->setAttribute('x', 57);
            $text->setAttribute('y', $val_text);
            $text->setAttribute('text-anchor', 'start');
            $text->setAttribute('id', 'reftext');
            $text->setAttribute('fill', $item[0]);
            $text->setAttribute('font-size', '11px');
            $text->setAttribute('font-family', "'DejaVu sans', Verdana, sans-serif");

            // Append elemets
            $this->xml_elements['static_background']->appendChild($path);
            $this->xml_elements['static_background']->appendChild($text);

            $pstart = $val_path;
            $tstart = $val_text;
        }
    }

    function drawTable($scale, $width)
    {
        // Create left scale
        $top = TRUE;
        $start = -17;

        foreach ($scale as $level)
        {
            $type = $this->type;
            
            if(($type == 'share') || ($type == 'others'))
                $level = formatBytes($level);
            
            if ($top)
                $color = '#CED0D5';
            else
                $color = '#EAEAEA';

            $m = $start + 50;

            $path = $this->xml_object->createElement('path');
            $path->setAttribute('d', 'M 38, '.$m.' L '.$width.', '.$m);
            $path->setAttribute('stroke', $color);
            $path->setAttribute('stroke-width', '1px');

            $text = $this->xml_object->createElement('text', $level);
            $text->setAttribute('x', 34);
            $text->setAttribute('y', ($m + 3));
            $text->setAttribute('text-anchor', 'end');
            $text->setAttribute('class', 'refleft');

            $this->xml_elements['static_background']->appendChild($path);
            $this->xml_elements['static_background']->appendChild($text);

            $top = FALSE;
            $start = $m;
        }

        // Add zero
        $text = $this->xml_object->createElement('text', 0);
        $text->setAttribute('x', 34);
        $text->setAttribute('y', 236);
        $text->setAttribute('text-anchor', 'end');
        $text->setAttribute('class', 'refleft');

        $this->xml_elements['static_background']->appendChild($text);
    }

    function drawChart($scale, $width)
    {
        if (isset($this->datas[0]) && is_array($this->datas[0]))
        {
            $foreached_datas = $this->datas[0];
            $onlykeys_datas = array_keys($this->datas[0]);
            $secondary_datas = array_keys($this->datas[1]);
        }
        else
        {
            $foreached_datas = $this->datas;
            $onlykeys_datas = array_keys($this->datas);
            $secondary_datas = FALSE;
        }

        // Create graphics data
        $defs = $this->xml_object->createElement('defs');

        $rect = $this->xml_object->createElement('rect');
        $rect->setAttribute('id', 'focusbar');
        $rect->setAttribute('width', 14);
        $rect->setAttribute('height', 211);
        $rect->setAttribute('x', -20);
        $rect->setAttribute('y', 34);
        $rect->setAttribute('style', 'fill: black; opacity: 0;');
        $defs->appendChild($rect);

        $path = $this->xml_object->createElement('path');
        $path->setAttribute('id', 'bubble');

        if ($this->evolution)
            $path->setAttribute('d', 'M 4.7871575,0.5 L 39.084404,0.5 C 41.459488,0.5 43.371561,2.73 43.371561,5.5 L 43.371561,25.49999 L 43.30,31.05 L 4.7871575,30.49999 C 2.412072,30.49999 0.5,28.26999 0.5,25.49999 L 0.5,5.5 C 0.5,2.73 2.412072,0.5 4.7871575,0.5 z');
        elseif ($secondary_datas)
            $path->setAttribute('d', 'M 1,0 v 8 l -6,-10 c -1.5,-2 -1.5,-2 -6,-2 h -36                        c -3,0 -6,-3 -6,-6 v -28 c 0,-3 3,-6 6,-6 h 43 c 3,0 6,3 6,6 z');
        else
            $path->setAttribute('d', 'M 4.7871575,0.5 L 39.084404,0.5 C 41.459488,0.5 43.371561,2.73 43.371561,5.5 L 43.371561,25.49999 C 43.371561,27.07677 43.83887,41.00777 42.990767,40.95796 C 42.137828,40.90787 37.97451,30.49999 36.951406,30.49999 L 4.7871575,30.49999 C 2.412072,30.49999 0.5,28.26999 0.5,25.49999 L 0.5,5.5 C 0.5,2.73 2.412072,0.5 4.7871575,0.5 z');

        $path->setAttribute('fill', 'none');
        $path->setAttribute('fill-opacity', '0.85');
        $path->setAttribute('pointer-events', 'none');
        $path->setAttribute('stroke-linejoin', 'round');
        $path->setAttribute('stroke', 'none');
        $path->setAttribute('stroke-opacity', '0.8');
        $path->setAttribute('stroke-width', '1px');
        $defs->appendChild($path);

        $rect = $this->xml_object->createElement('rect');
        $rect->setAttribute('id', 'graphicbar');
        $rect->setAttribute('width', '12');
        $rect->setAttribute('height', '200');
        $rect->setAttribute('rx', '2');
        $rect->setAttribute('ry', '1');
        $rect->setAttribute('fill', '#6C84C0');
        $rect->setAttribute('fill-opacity', '0.6');
        $rect->setAttribute('stroke', '#5276A9');
        $rect->setAttribute('stroke-width', '1px');
        $defs->appendChild($rect);

        $rect = $this->xml_object->createElement('rect');
        $rect->setAttribute('style', 'fill:#8B2323');
        $rect->setAttribute('id', 'rectpoint');
        $rect->setAttribute('width', 4);
        $rect->setAttribute('height', 4);
        $defs->appendChild($rect);

        $this->xml_elements['chart_defs'] = $defs;
        $global_g = $this->xml_object->createElement('g');

        // Calc
        $x_base = 35;
        $y_base = 20;
        $start = 18;
        $element = 0;

        $chart_defs = '';
        $xprevious = 38;
        $tprevious = 233;

        foreach ($foreached_datas as $key => $data)
        {
            $x = 27 + $x_base;
            $y = 107 + $y_base;

            $top = 233 - ceil($data / ($scale[4] / 100) * 2);

            if ($top <= 50)
                $bubble_top = 55;
            elseif (!$secondary_datas)
                $bubble_top = ($top - 42);
            elseif ($secondary_datas)
                $bubble_top = ($top - 10);

            $type = $this->type;

            if(($type == 'share') || ($type == 'others'))
                $value = formatBytes($data);
            else
                $value = $data;
            
            // Create the chart with datas
            $g = $this->xml_object->createElement('g');
            $g->setAttribute('transform', 'translate('.$x.')');

            $duse = $this->xml_object->createElement('use');
            $duse->setAttribute('xlink:href', '#mark');
            $duse->setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink');
            $g->appendChild($duse);

            $data_g = $this->xml_object->createElement('g');
            $data_g->setAttribute('class', 'gbar');

            if ($this->link)
            {
                $text = $this->xml_object->createElement('text');            

                $link = $this->xml_object->createElement('a', mb_substr(filterSpecialXML($onlykeys_datas[$element]), 0, 7));
                $link->setAttribute('xlink:href', str_replace('{data}', filterSpecialXML($onlykeys_datas[$element]), $this->link));
                $link->setAttribute('target', '_main');
                $text->appendChild($link);
            }
            else
                $text = $this->xml_object->createElement('text', mb_substr(filterSpecialXML($onlykeys_datas[$element]), 0, 7));

            $text->setAttribute('class', 'reftext');
            $text->setAttribute('y', 248);
            $text->setAttribute('text-anchor', 'middle');

            $data_g->appendChild($text);

            $uselink = $this->xml_object->createElement('use');
            $uselink->setAttribute('xlink:href', '#focusbar');
            $uselink->setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink');
            $data_g->appendChild($uselink);

            if (!$this->evolution)
            {
                $rect = $this->xml_object->createElement('rect');
                $rect->setAttribute('class', 'bluebar');
                $rect->setAttribute('height', (233 - $top));
                $rect->setAttribute('width', 13);
                $rect->setAttribute('x', -6);
                $rect->setAttribute('y', $top);
                $rect->setAttribute('fill', $this->legend[0][0]);
                $rect->setAttribute('fill-opacity', '0.6');
                $data_g->appendChild($rect);
            }
            else
            {
                $use = $this->xml_object->createElement('use');
                $use->setAttribute('xlink:href', '#rectpoint');
                $use->setAttribute('y', ($top - 1));
                $use->setAttribute('x', -2);
                $data_g->appendChild($use);

                if ($x != (35 + 27))
                    $chart_defs .= 'L '.$x.' '.$top.'  ';
                else
                    $chart_defs .= 'M '.$xprevious.' '.$tprevious.' L '.$x.' '.$top.'  ';

                $xprevious = $x;
                $tprevious = $top;
            }

            if ($secondary_datas && isset($secondary_datas[$element]))
            {
                $datalink = $secondary_datas[$element];
                $dataval = $this->datas[1][$datalink];
                $stop = 233 - ceil($dataval / ($scale[4] / 100) * 2);

                $rect = $this->xml_object->createElement('rect');
                $rect->setAttribute('class', 'redbar');
                $rect->setAttribute('height', (233 - $stop));
                $rect->setAttribute('width', 13);
                $rect->setAttribute('x', -6);
                $rect->setAttribute('y', $stop);
                $rect->setAttribute('fill', $this->legend[1][0]);
                $rect->setAttribute('fill-opacity', '0.7');
                $data_g->appendChild($rect);
            }

            if (!$this->evolution)
            {
                $path = $this->xml_object->createElement('path');
                $path->setAttribute('stroke', '#5276A9');
                $path->setAttribute('stroke-width', '2px');
                $path->setAttribute('fill', 'none');
                $path->setAttribute('d', 'M -7,233 v -'.(232 - $top).' c 0,-1 1,-1 1,-1 h 12 c 1,0 2,0 2,1 v '.(232 - $top).' z');
                $data_g->appendChild($path);
            }

            $uselink = $this->xml_object->createElement('use');
            $uselink->setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink');
            $uselink->setAttribute('xlink:href', '#bubble');
            $uselink->setAttribute('y', $bubble_top);

            if (!$secondary_datas)
                $uselink->setAttribute('x', -42);

            $data_g->appendChild($uselink);

            $text = $this->xml_object->createElement('text', $value);
            $text->setAttribute('class', 'bubbletextblue');
            $text->setAttribute('x', -10);

            if (!$secondary_datas)
                $text->setAttribute('y', ($bubble_top + 20));
            else
                $text->setAttribute('y', ($bubble_top - 27));

            $text->setAttribute('fill', 'none');
            $data_g->appendChild($text);

            if ($secondary_datas && isset($secondary_datas[$element]))
            {
                $text = $this->xml_object->createElement('text', $dataval);
                $text->setAttribute('class', 'bubbletextred');
                $text->setAttribute('x', -10);
                $text->setAttribute('y', ($bubble_top - 11));
                $text->setAttribute('fill', 'none');
                $data_g->appendChild($text);
            }

            $g->appendChild($data_g);
            $global_g->appendChild($g);

            $x_base = $x_base + 50;
            $y_base = $y_base + 20;
            $element ++;            
        }

        if ($this->evolution)
        {
            $path = $this->xml_object->createElement('path');
            $path->setAttribute('d', $chart_defs);
            $path->setAttribute('stroke', $this->legend[0][0]);
            $path->setAttribute('stroke-width', '1px');
            $path->setAttribute('fill', 'none');
            $this->xml_elements['evolution_path'] = $path;
        }

        $this->xml_elements['global_g'] = $global_g;

        $path = $this->xml_object->createElement('path');
        $path->setAttribute('d', 'M 38,233 h '.$width);
        $path->setAttribute('stroke', '#2F4F77');
        $path->setAttribute('stroke-width', '2px');
        $path->setAttribute('pointer-events', 'none');
        $this->xml_elements['final_path'] = $path;
    }

    function has_errors()
    {
        return $this->has_errors;
    }

    function getXMLOutput()
    {
        if (isset($this->xml_object))
        {
            // Add SVG elements to the DOM object
            foreach($this->xml_elements as $element)
                $this->svg->appendChild($element);

            // Return the XML
            $this->xml_object->formatOutput = true; 
            return $this->xml_object->saveXML();
        }
    }
}
?>
