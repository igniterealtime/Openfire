# addressparser

Parse e-mail address fields

## Installation

Install with npm

    npm install addressparser

## Usage

Include the module

    var addressparser = require("addressparser");

Parse some address strings with addressparser(field)

    var addresses = addressparser("andris <andris@tr.ee>");
    console.log(addresses); // [{name: "andris", address:"andris@tr.ee"}]

And when using groups

    addressparser('Composers:"Bach, Sebastian" <sebu@example.com>, mozart@example.com (Mozzie);');

the result is

    [
        {
            name: "Composers",
            group: [
                {
                    address: "sebu@example.com",
                    name: "Bach, Sebastian"
                },
                {
                    address: "mozart@example.com",
                    name: "Mozzie"
                }
            ]
        }
    ]


## Notes

  * **NB!** this module does not decode any mime-word or punycode encoded strings, it is only a basic parser for parsing the base data, you need to decode the encoded parts later by yourself

## License

**MIT**