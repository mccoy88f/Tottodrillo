"""The Vimms-DL Tool"""
from prettytable import PrettyTable
import zipfile
import re
import sys
import os
from typing import List
import py7zr
from requests.models import Response
import requests
from bs4 import BeautifulSoup
from src.helpers import models
from src import helpers


def get_rom_download_url(url: str) -> str:
    """Gets the Download ID for the a specific ROM from the ROMs page url"""
    download_id: str = ''
    try:
        page: Response = requests.get('https://vimm.net/' + url)
        soup: BeautifulSoup = BeautifulSoup(page.content, 'html.parser')
        result = soup.find(id='download_form')
        result = result.find(attrs={'name': 'mediaId'})
        download_id: str = result['value']
    except:
        e = sys.exc_info()[0]
        print('Failed on getting ROM ID')
        print(e)
    return download_id


def get_sub_section_letter_from_str(subsection: str) -> str:
    """Returns the subsection letter to get the downloaded ROM to the\
            correct alphanumeric directory"""
    number: str = '&section=number'
    if number in subsection.lower():
        return 'number'
    else:
        return subsection[-1]


def get_section_of_roms(section: str) -> List[models.ROM]:
    """Gets a section of ROM home page URIs from a system category"""
    roms: List[models.ROM] = []
    try:
        page: Response = requests.get('https://vimm.net/vault/' + section)
        soup = BeautifulSoup(page.content, 'html.parser')
        result = soup.find(
            'table', {'class': 'rounded centered cellpadding1 hovertable'})
        for j in result.contents:
            if j != '\n':
                new_soup = BeautifulSoup(str(j), 'html.parser')
                odd = new_soup.find(attrs={'class': 'odd'})
                even = new_soup.find(attrs={'class': 'even'})
                if odd is not None:
                    result_soup = BeautifulSoup(str(odd.contents[0]),
                                                'html.parser')
                    result = result_soup.find('a', href=True)
                    name = result.contents[0]
                    result = result['href']
                    rom = models.ROM(name, result)
                    roms.append(rom)
                    odd = None
                if even is not None:
                    result_soup = BeautifulSoup(str(even.contents[0]),
                                                'html.parser')
                    result = result_soup.find('a', href=True)
                    name = result.contents[0]
                    result = result['href']
                    rom = models.ROM(name, result)
                    roms.append(rom)
                    even = None
    except:
        e = sys.exc_info()[0]
    return roms


def get_every_system_roms() -> List[models.BulkSystemROMS]:
    every_rom: List[models.BulkSystemROMS] = []
    for i in range(0, 17):
        system_roms: models.BulkSystemROMS = get_all_system_roms(
            helpers.selection_to_uri(helpers.get_selection_from_num(i)))
        every_rom.append(system_roms)
    return every_rom


def get_all_system_roms(system: str) -> models.BulkSystemROMS:
    """Used in bulk mode to get the home page URI for every rom on a system"""
    print('Getting a list of roms for the ' + system)
    section_roms: List[models.SectionofROMs] = []
    section_urls: List[str] = [
        f'?p=list&system={system}&section=number', f'{system}/a',
        f'{system}/b', f'{system}/c', f'{system}/d', f'{system}/e',
        f'{system}/f', f'{system}/g', f'{system}/h', f'{system}/i',
        f'{system}/j', f'{system}/k', f'{system}/l', f'{system}/m',
        f'{system}/n', f'{system}/o', f'{system}/p', f'{system}/q',
        f'{system}/r', f'{system}/s', f'{system}/t', f'{system}/u',
        f'{system}/v', f'{system}/w', f'{system}/x', f'{system}/y',
        f'{system}/z'
    ]
    for x in section_urls:
        roms: List[models.ROM] = get_section_of_roms(x)
        section: models.SectionofROMs = models.SectionofROMs(x, roms)
        section_roms.append(section)
    system_roms: models.BulkSystemROMS = models.BulkSystemROMS(
        section_roms, system)
    return system_roms


def download_file(page_url: str, download_url: str, path: str) -> str:
    """Downloads one rom from the uri, downloadid\
            downloads to the path director"""
    x: int = 0
    filename: str = ''
    while True:
        headers: dict[str, str] = {
            'Accept':
            'text/html,application/xhtml+xml,application/xml;q=0.9,image' +
            '/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9',
            'Accept-Encoding':
            'gzip, deflate, br',
            'Connection':
            'keep-alive',
            'User-Agent':
            helpers.get_random_ua(),
            'Referer':
            f'https://vimm.net/vault{page_url}'
        }
        file: Response = requests.get(
            f'https://download2.vimm.net/download/?mediaId={download_url}',
            headers=headers,
            allow_redirects=True)
        if file.status_code == 200:
            filename = file.headers['Content-Disposition']
            filenames: List[str] = re.findall(r'"([^"]*)"', filename)
            filename = filenames[0]
            full_path = os.path.join(path, filename)
            open(full_path, 'wb').write(file.content)
            print('Downloaded ' + filename + '!')
            break
        if x == 4:
            print(f'5 Requests made to {download_url} and failed')
            break
        if file.status_code != 200:
            x += 1
            continue
    return filename


def get_search_selection(config: models.Config) -> models.Config:
    """Gets search criteria for search mode"""
    search_selection: models.SearchSelection = models.SearchSelection()
    print('\nPlease select what system you want to search')
    print('Press Enter to do a general site wide search')
    helpers.print_console_list()
    while True:
        user_input: str = sys.stdin.readline()
        try:
            if user_input == '\n':
                search_selection.System = 'general'
                config.Query.SearchSelections = search_selection
                break
            if not (int(user_input) > 17 or int(user_input) < 0):
                search_selection.System = \
                    helpers.get_selection_from_num(int(user_input))
                config.Query.SearchSelections = search_selection
                break
            else:
                print('Not a selection')
                print('Please select a value from the list')
        except ValueError:
            print('Please select a value from the list')
            continue
    print('Input what rom you want to search for')
    search_selection.Query = sys.stdin.readline()
    return config


def get_system_search_section(
        search_selection: models.SearchSelection) -> List[models.ROM]:
    """Gets a section of roms using system search from the search selection"""
    roms: List[models.ROM] = []
    try:
        page = requests.get(helpers.get_search_url(search_selection))
        soup: BeautifulSoup = BeautifulSoup(page.content, 'html.parser')
        result = soup.find(
            'table', {'class': 'rounded centered cellpadding1 hovertable'})
        for j in result.contents:
            if j != '\n':
                new_soup: BeautifulSoup = BeautifulSoup(str(j), 'html.parser')
                odd = new_soup.find(attrs={'class': 'odd'})
                even = new_soup.find(attrs={'class': 'even'})
                if odd is not None:
                    result_soup: BeautifulSoup = BeautifulSoup(
                        str(odd.contents[0]), 'html.parser')
                    result = result_soup.find('a', href=True)
                    name = result.contents[0]
                    result = result['href']
                    rom = models.ROM(name, result)
                    roms.append(rom)
                    odd = None
                if even is not None:
                    result_soup = BeautifulSoup(str(even.contents[0]),
                                                'html.parser')
                    result = result_soup.find('a', href=True)
                    name = result.contents[0]
                    result = result['href']
                    rom = models.ROM(name, result)
                    roms.append(rom)
                    even = None
    except BaseException:
        e = sys.exc_info()[0]
        print('Failed on system search section')
        print(e)
    return roms


def get_general_search_section(
        search_selection: models.SearchSelection) -> List[models.ROM]:
    """Gets a section of roms when using general search from the search selection"""
    roms: List[models.ROM] = []
    try:
        page = requests.get(helpers.get_search_url(search_selection))
        soup: BeautifulSoup = BeautifulSoup(page.content, 'html.parser')
        result = soup.find(
            'table', {'class': 'rounded centered cellpadding1 hovertable'})
        for j in result.contents:
            if j != '\n':
                new_soup: BeautifulSoup = BeautifulSoup(str(j), 'html.parser')
                odd = new_soup.find(attrs={'class': 'odd'})
                even = new_soup.find(attrs={'class': 'even'})
                if odd is not None:
                    system_result_soup: BeautifulSoup = BeautifulSoup(
                        str(odd.contents[0]), 'html.parser')
                    name_result_soup: BeautifulSoup = BeautifulSoup(
                        str(odd.contents[1]), 'html.parser')
                    system_result = system_result_soup.find('td')
                    system = system_result.contents[0]
                    name_result = name_result_soup.find('a', href=True)
                    name = name_result.contents[0]
                    game_id = name_result['href']
                    rom = models.ROM(name, game_id, system)
                    roms.append(rom)
                    odd = None
                if even is not None:
                    system_result_soup: BeautifulSoup = BeautifulSoup(
                        str(even.contents[0]), 'html.parser')
                    name_result_soup: BeautifulSoup = BeautifulSoup(
                        str(even.contents[1]), 'html.parser')
                    system_result = system_result_soup.find('td')
                    system = system_result.contents[0]
                    name_result = name_result_soup.find('a', href=True)
                    name = name_result.contents[0]
                    game_id = name_result['href']
                    rom = models.ROM(name, game_id, system)
                    roms.append(rom)
                    even = None
    except BaseException:
        e = sys.exc_info()[0]
        print('Failed getting general search section')
        print(e)
    return roms


def get_program_mode() -> models.Config:
    """Gets input from user to go into either (Bulk/Search) mode"""
    config: models.Config = models.Config()
    print(
        '\nWould you like to bulk download roms for systems or search for specific roms? (B/s)'
    )
    print("For bulk mode use 'b' and search mode use 's'")
    print('Default is \'b\'')
    while True:
        user_input: str = sys.stdin.readline()
        if user_input == '\n':
            config.BulkMode = True
            break
        if user_input.lower() == 'b\n':
            config.BulkMode = True
            break
        if user_input.lower() == 's\n':
            config.SearchMode = True
            break
        else:
            print('Not a selection')
            print('Please Select B/s')
            continue
    return config


def get_bulk_selections(config: models.Config) -> models.Config:
    """Gets input in bulk mode if the user wants to only download specific consoles"""
    print("Press Enter to download all of Vimm's roms or select from the" +
          " following of what systems you would like to download")
    print('Enter \'d\' when finished if choosing specific consoles\n')
    helpers.print_console_list()
    while True:
        user_input: str = sys.stdin.readline()
        if user_input == '\n' and len(config.Selections) == 0:
            config.All = True
            break
        if user_input == 'd\n':
            break
        try:
            if not (int(user_input) > 17 or int(user_input) < 0):
                config.Selections.append(int(user_input))
            else:
                print('Not a selection')
                print('Please select a value from the list')
        except ValueError:
            print('Please select a value from the list')
            continue
    return config


def get_extraction_status(config: models.Config) -> models.Config:
    """Used in Bulk and Search mode to check if user wants to \
            extract and delete downloaded ROM archives"""
    print(
        'Would you like to automatically extract and delete archives after download? (Y/n)'
    )
    print('Default is \'y\'')
    while True:
        user_input: str = sys.stdin.readline()
        if user_input == '\n':
            config.Extract = True
            break
        if user_input.lower() == 'y\n':
            config.Extract = True
            break
        if user_input.lower() == 'n\n':
            config.Extract = False
            break
        if (user_input.lower() != 'n\n') and (user_input.lower() != 'y\n'):
            print('Not a selection')
            print('Please Select Y/n')
            continue
    return config


def print_general_search(roms: List[models.ROM]):
    table = PrettyTable()
    table.field_names = ["Selection Number", "System", "ROM"]
    count = 0
    print(
        "\nSelect which roms you would like to download and then enter 'd'\n")
    for x in roms:
        table.add_row([count, x.Console, x.Name])
        count += 1
    table.align = "l"
    table.right_padding_width = 0
    print(table)


def print_system_search(roms: List[models.ROM]):
    """Prints the results from a system search"""
    table = PrettyTable()
    table.field_names = ["Selection Number", "ROM"]
    count: int = 0
    print(
        '\nSelect which roms you would like to download and then enter \'d\'')
    for x in roms:
        table.add_row([count, x.Name])
        count += 1
    table.align = "l"
    table.right_padding_width = 0
    print(table)


def print_search_results(roms: List[models.ROM]) -> None:
    """Prints the returned search results from the users query"""
    if roms[0].Console != '':
        print_general_search(roms)
    else:
        print_system_search(roms)


def get_search_result_input(roms: List[models.ROM]) -> List[int]:
    """Used to get input in search mode for what ROMs the user wants to download"""
    download_sel_roms: List[int] = []
    print(
        '\nSelect which roms you would like to download and then enter \'d\'')
    while True:
        user_input = sys.stdin.readline()
        if user_input == '\n':
            print('Please select a rom or press \'q\' to quit program')
            continue
        if user_input == 'q\n':
            exit()
        if user_input == 'd\n':
            break
        try:
            if not (int(user_input) > len(roms) - 1 or int(user_input) < 0):
                download_sel_roms.append(int(user_input))
            else:
                print('Not a selection')
                print('Please select a value from the list')
        except ValueError:
            print('Please select a value from the list')
            continue
    return download_sel_roms


def download_search_results(downloads: List[int], roms: List[models.ROM],
                            config: models.Config) -> None:
    """Downloads the users specified roms in search mode"""
    for x in downloads:
        download_name = download_file(roms[x].URI,
                                      get_rom_download_url(roms[x].URI), '.')
        if config.Extract:
            extract_and_delete_search_results('.',download_name)


def extract_file(path: str, name: str) -> None:
    """Extracts the downloaded archives"""
    full_path: str = os.path.join(path, name)
    base_filename: List[str] = re.findall(r'(.+?)(\.[^.]*$|$)', name)
    file_name: str = str(base_filename[0][0])
    file_type = re.findall(r'(zip|7z)', full_path)
    if str(file_type[0]).lower() == 'zip':
        with (zipfile.ZipFile(full_path, 'r')) as z:
            dir_path = create_directory_for_rom(file_name, path)
            z.extractall(os.path.join(dir_path))
    if str(file_type[0]).lower() == '7z':
        with py7zr.SevenZipFile(full_path, mode='r') as z:
            dir_path = create_directory_for_rom(file_name, path)
            z.extractall(dir_path)


def delete_file(path: str, name: str) -> None:
    """Deletes the archives"""
    os.remove(os.path.join(path, name))


def check_if_need_to_re_search() -> bool:
    """Gets user input to research if query didn't return wanted results"""
    search: bool = False
    print('Do you want to search again?(y/N)')
    while True:
        user_input = sys.stdin.readline()
        if user_input == '\n':
            break
        if user_input.lower() == 'y\n':
            search = True
            break
        if user_input.lower() == 'n\n':
            break
        if (user_input.lower() != 'n\n') and user_input.lower() != 'y\n':
            print('Not a selection')
            print('Please Select y/N')
            continue
    return search


def run_search(config: models.Config) -> List[models.ROM]:
    """Runs the correct search method to get a list of the search results"""
    if helpers.is_general_search(config.Query.SearchSelections):
        roms: List[models.ROM] = get_general_search_section(
            config.Query.SearchSelections)
        return roms
    roms: List[models.ROM] = get_system_search_section(
        config.Query.SearchSelections)
    return roms


def create_directory_for_rom(name: str, path: str) -> str:
    """Used to create the directory for the ROMs archived files to\
            be extracted to"""
    new_path: str = os.path.join(path, name)
    os.mkdir(new_path)
    return new_path


def run_search_loop(config: models.Config) -> None:
    """Main loop for the search program"""
    while True:
        config = get_search_selection(config)
        roms: List[models.ROM] = run_search(config)
        print_search_results(roms)
        restart: bool = check_if_need_to_re_search()
        if restart:
            continue
        downloads: List[int] = get_search_result_input(roms)
        download_search_results(downloads, roms, config)
        print('Done!')
        restart = check_if_need_to_re_search()
        if restart:
            continue
        else:
            exit()

# TODO FIX THREADING
def extract_and_delete_search_results(path: str, download: str) -> None:
    """Used to extract and delete the archives in search mode"""
    extract_file(path, download)
    print('Finished extracting ' + download + '!')
    delete_file(path, download)


def get_user_sel_bulk_roms(
        config: models.Config) -> List[models.BulkSystemROMS]:
    selected_bulk: List[models.BulkSystemROMS] = []
    for i in config.Selections:
        system_roms: models.BulkSystemROMS =\
            get_all_system_roms(helpers.selection_to_uri(helpers.get_selection_from_num(i)))
        selected_bulk.append(system_roms)
    return selected_bulk


def download_bulk_roms(config: models.Config,
                       roms: List[models.BulkSystemROMS]):
    for system in roms:
        print('Starting to download all roms for the ' + system.System + '!')
        for section in system.Sections:
            for rom in section.ROMS:
                download_name = download_file(rom.URI,
                                              get_rom_download_url(rom.URI),
                                              section.Path)
                if config.Extract:
                    extract_and_delete_search_results(section.Path,download_name)



def run_selected_program(config: models.Config) -> None:
    """Runs selected program"""
    if config.BulkMode:
        config = get_bulk_selections(config)
        if config.All:
            all_roms: List[models.BulkSystemROMS] = get_every_system_roms()
            all_roms: List[
                models.BulkSystemROMS] = helpers.generate_path_to_bulk_roms(
                    all_roms)
            helpers.create_directory_structure(config, os.getcwd())
            download_bulk_roms(config, all_roms)
            exit()
        else:
            user_selected_bulk: List[
                models.BulkSystemROMS] = get_user_sel_bulk_roms(config)
            user_selected_bulk: List[
                models.BulkSystemROMS] = helpers.generate_path_to_bulk_roms(
                    user_selected_bulk)
            helpers.create_directory_structure(config, os.getcwd())
            download_bulk_roms(config, user_selected_bulk)
            exit()
    if config.SearchMode:
        run_search_loop(config)


def main() -> None:
    """Programs main method"""
    helpers.print_welcome()
    config: models.Config = get_program_mode()
    config: models.Config = get_extraction_status(config)
    run_selected_program(config)


if __name__ == '__main__':
    main()
