"""Helper functions for the main script"""
from typing import List
import os
import random
import sys
from src import models

__selections = [
    {
        0: 'NES'
    },
    {
        1: 'Genesis'
    },
    {
        2: 'SNES'
    },
    {
        3: 'Saturn'
    },
    {
        4: 'Playstation'
    },
    {
        5: 'N64'
    },
    {
        6: 'Dreamcast'
    },
    {
        7: 'Playstation-2'
    },
    {
        8: 'Xbox'
    },
    {
        9: 'Gamecube'
    },
    {
        10: 'Playstation-3'
    },
    {
        11: 'Wii'
    },
    {
        12: 'WiiWare'
    },
    {
        13: 'Game-Boy'
    },
    {
        14: 'Game-Boy-Color'
    },
    {
        15: 'Game-Boy-Advanced'
    },
    {
        16: 'Nintendo-DS'
    },
    {
        17: 'PSP'
    },
]
__user_agents: List[str] = [
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' +
    ' AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36' +
    ' (KHTML, like Gecko) Chrome/92.0.4515.107 Safari/537.36',
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36' +
    ' (KHTML, like Gecko) Chrome/91.0.4472.114 Safari/537.36',
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)'
    + ' Chrome/91.0.4472.164 Safari/537.36',
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:90.0) Gecko/20100101 Firefox/90.0',
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:89.0) Gecko/20100101 Firefox/89.0',
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko)'
    + ' Version/14.1.1 Safari/605.1.15',
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)'
    + ' Chrome/92.0.4515.107 Safari/537.36',
    'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko)' +
    ' Chrome/91.0.4472.114 Safari/537.36'
]
__to_uri = {
    'NES': 'NES',
    'Genesis': 'Genesis',
    'SNES': 'SNES',
    'Saturn': 'Saturn',
    'Playstation': 'PS1',
    'N64': 'N64',
    'Dreamcast': 'Dreamcast',
    'Playstation-2': 'PS2',
    'Xbox': 'Xbox',
    'Gamecube': 'GameCube',
    'Playstation-3': 'PS3',
    'Wii': 'Wii',
    'WiiWare': 'WiiWare',
    'Game-Boy': 'GB',
    'Game-Boy-Color': 'GBC',
    'Game-Boy-Advanced': 'GBA',
    'Nintendo-DS': 'DS',
    'PSP': 'PSP',
}


def __create_alpha_num_structure(path: str, system: str):
    """Used in bulk mode to create the Alphanumeric directory\
             structure in a system's directory"""
    dirnames: list[str] = [
        '#', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
        'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'
    ]
    try:
        for x in dirnames:
            os.mkdir(os.path.join(path, 'ROMS', system, x))
    except:
        e = sys.exc_info()[0]
        print('Failed Creating AlphaNum Structure')
        print(e)


def __create_rom_home_dir(path: str):
    """Creates the main 'ROMS' directory in the root of the project"""
    try:
        os.mkdir(os.path.join(path, 'ROMS'))
    except:
        e = sys.exc_info()[0]
        print('Failed Creating Home Directory')
        print(e)


def __create_rom_system_dir(path: str, system: str):
    """Creates a specific system's directory inside the 'ROMS' folder"""
    try:
        os.mkdir(os.path.join(path, 'ROMS', system))
    except:
        e = sys.exc_info()[0]
        print('Failed Creating ROM System Directory')
        print(e)


def __check_if_home_dir_created(path: str):
    """Checks for the existence of the 'ROMS' directory"""
    for x in os.listdir(path):
        if x == 'ROMS':
            return True


def __check_if_system_dir_created(path: str, system: str):
    """Checks for the existence of a specific system directory in the 'ROMS' directory"""
    for x in os.listdir(os.path.join(path, 'ROMS')):
        if x == system:
            return True


def __create_all_no_home(path: str):
    """Used in bulk mode to create all the directories and sub-directories"""
    __create_rom_home_dir(path)
    for x in __selections:
        for value in x:
            if not __check_if_system_dir_created(path, x[value]):
                __create_rom_system_dir(path, x[value])
                __create_alpha_num_structure(path, x[value])


def __create_all_w_home(path: str):
    """Used in bulk mode to create all the directories and sub-directories \
            except 'ROMS' Directory"""
    for x in __selections:
        for value in x:
            if not __check_if_system_dir_created(path, x[value]):
                __create_rom_system_dir(path, x[value])
                __create_alpha_num_structure(path, x[value])


def __create_sel_w_home(path: str, user_selections: List[str]):
    """Used in bulk mode when the user only wants selected systems if home dir is already created"""
    for x in user_selections:
        if not __check_if_system_dir_created(path,
                                             __selections[int(x)][int(x)]):
            __create_rom_system_dir(path, __selections[int(x)][int(x)])
            __create_alpha_num_structure(path, __selections[int(x)][int(x)])


def __create_sel_no_home(path: str, user_selections: List[str]):
    """Used in bulk mode when the user only wants selected systems"""
    __create_rom_home_dir(path)
    for x in user_selections:
        if not __check_if_system_dir_created(path,
                                             __selections[int(x)][int(x)]):
            __create_rom_system_dir(path, __selections[int(x)][int(x)])
            __create_alpha_num_structure(path, __selections[int(x)][int(x)])


def create_directory_structure(config: models.Config, path: str):
    """Public helper to be used in bulk mode"""
    if config.Selections != None:
        if config.All:
            if not __check_if_home_dir_created(path):
                __create_all_no_home(path)
            if __check_if_home_dir_created(path):
                __create_all_w_home(path)
        if not config.All:
            if not __check_if_home_dir_created(path):
                __create_sel_no_home(path, config.Selections)
            if __check_if_home_dir_created(path):
                __create_sel_w_home(path, config.Selections)


def selection_to_uri(selection: str):
    """Public helper to return the correct uri from the prettier printed version"""
    return __to_uri[selection]


def print_console_list():
    """Public helper to print the consoles listed on Vimms"""
    for x in range(0, 9):
        print(
            f'{x:5d} ==> {__selections[x][x]:15} | {x+9:5d} ==> {__selections[x+9][x+9]:10}'
        )


def get_selection_from_num(selection: int):
    """Returns the specified selection from __selections"""
    return __selections[selection][selection]


def get_random_ua() -> str:
    """Returns a random user agent for download method"""
    index: int = random.randint(0, len(__user_agents) - 1)
    return __user_agents[index]


def print_welcome():
    """Prints the welcome message..\
             hmm yes the floor is made of floor"""
    print(r"""
     _   _ _                          _           _     ______                    _                 _
    | | | (_)                        | |         (_)    |  _  \                  | |               | |
    | | | |_ _ __ ___  _ __ ___  ___ | |     __ _ _ _ __| | | |_____      ___ __ | | ___   __ _  __| | ___ _ __
    | | | | | '_ ` _ \| '_ ` _ \/ __|| |    / _` | | '__| | | / _ \ \ /\ / / '_ \| |/ _ \ / _` |/ _` |/ _ \ '__|
    \ \_/ / | | | | | | | | | | \__ \| |___| (_| | | |  | |/ / (_) \ V  V /| | | | | (_) | (_| | (_| |  __/ |
     \___/|_|_| |_| |_|_| |_| |_|___/\_____/\__,_|_|_|  |___/ \___/ \_/\_/ |_| |_|_|\___/ \__,_|\__,_|\___|_|
        """)
    print('Welcome to the Vimm\'s Lair Download Script')
    print('Please use responsibly, I am not liable for any damages' +
          ' or legal issues caused by using this script')


def get_search_url(search_selection: models.SearchSelection) -> str:
    """Returns a hydrated search_url with the correct system and query"""
    if search_selection.System != 'general':
        url: str = ('https://vimm.net/vault/?p=list&system=' +
                    f'{selection_to_uri(search_selection.System)}' +
                    f'&q={search_selection.Query}')
        return url
    else:
        url: str = (
            f'https://vimm.net/vault/?p=list&q={search_selection.Query}')
        return url


def is_general_search(search_selection: models.SearchSelection):
    """Returns bool on if it is a general search or not"""
    if search_selection.System == 'general':
        return True
    return False


def generate_path_to_bulk_roms(
        roms: List[models.BulkSystemROMS]) -> List[models.BulkSystemROMS]:
    """Creates the absolute path to where each rom should be downloaded"""
    for system in roms:
        for section in system.Sections:
            if not 'number' in section.Section:
                path: str = os.path.join(os.getcwd(), "ROMS", section.Section)
                section.Path = path
            else:
                path: str = os.path.join(os.getcwd(), "ROMS", system.System,
                                         '#')
                section.Path = path
    return roms
